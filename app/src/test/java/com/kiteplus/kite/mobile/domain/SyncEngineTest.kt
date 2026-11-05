package com.kiteplus.kite.mobile.domain

import com.kiteplus.kite.mobile.data.local.ContentEntity
import com.kiteplus.kite.mobile.data.local.FakeContentDao
import com.kiteplus.kite.mobile.data.remote.KiteGateway
import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The contract these tests pin down is the one the server publishes in its
 * OpenAPI document: a create takes its id from the response, an update carries
 * the revision it was made against, a 409 carries the other side's version, and
 * a pull asks only for what changed since the watermark.
 *
 * Two things about the wire shape are load-bearing here. A listing answers with
 * summaries, which carry no text at all — so a pull must never write an empty
 * article over a real one, and an item seen for the first time has to be read
 * whole before it is stored. And a summary has no `deleted_at`, so deletions
 * come from a separate pass over the trash.
 *
 * Every run ends with two list requests: the changes, then the deletions. A
 * missing response does not fail loudly — it sits until the read timeout, which
 * is exactly how these tests first came to be noticed.
 */
class SyncEngineTest {

    private lateinit var server: MockWebServer
    private lateinit var dao: FakeContentDao
    private lateinit var settings: TestSettings

    private class TestSettings(private val client: KiteGateway?) : SyncSettings {
        override fun api(): KiteGateway? = client
        override var syncWatermark: String? = null

        /** Preset so a run does not spend a request asking for the kinds. */
        override var defaultKind: String? = "post"
        override var contentTypesJson: String? = "[]"
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // A relative address on purpose: everything after `/api/v1/` is what is
        // under test, not the host.
        val api = KiteGateway.create(
            rootUrl = server.url("/").toString(),
            cookieJar = CookieJar.NO_COOKIES,
        )
        dao = FakeContentDao()
        settings = TestSettings(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a local draft is created on the server and takes its id`() = runBlocking {
        dao.upsert(
            ContentEntity(
                id = "local:abc",
                title = "草稿",
                body = "正文",
                dirty = true,
                isNew = true,
                localUpdatedAt = 1,
            ),
        )

        server.enqueue(json(SESSION))
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("ETag", "\"1\"")
                .setHeader("Location", "/api/v1/contents/srv-1")
                .setBody(item("srv-1", "草稿", "正文", revision = "1")),
        )
        server.enqueue(page())
        server.enqueue(deletions())

        val report = SyncEngine(dao, settings).sync()

        assertEquals(1, report.pushed)
        assertNull(report.error)

        val row = dao.byId("srv-1")
        assertNotNull(row)
        assertEquals("1", row!!.revision)
        assertEquals("正文", row.body)
        assertFalse(row.dirty)
        assertFalse(row.isNew)
        assertNull(dao.byId("local:abc"))

        assertEquals("/api/v1/auth/session", server.takeRequest().path)
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/api/v1/contents", create.path)
        // A create must not carry If-Match; the server refuses one outright.
        assertNull(create.getHeader("If-Match"))
        assertTrue(create.body.readUtf8().contains("\"kind\":\"post\""))
    }

    @Test
    fun `an edit carries the revision it was made against`() = runBlocking {
        dao.upsert(
            ContentEntity(
                id = "srv-1",
                title = "新标题",
                body = "新正文",
                revision = "7",
                dirty = true,
                localUpdatedAt = 2,
            ),
        )

        server.enqueue(json(SESSION))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"8\"")
                .setBody(item("srv-1", "新标题", "新正文", revision = "8")),
        )
        server.enqueue(page())
        server.enqueue(deletions())

        val report = SyncEngine(dao, settings).sync()

        assertEquals(1, report.pushed)
        assertNull(report.error)
        val row = dao.byId("srv-1")
        assertEquals("8", row!!.revision)
        assertFalse(row.dirty)

        server.takeRequest()
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/api/v1/contents/srv-1", put.path)
        assertEquals("7", put.getHeader("If-Match"))
    }

    @Test
    fun `a refused write keeps both versions`() = runBlocking {
        dao.upsert(
            ContentEntity(
                id = "srv-1",
                title = "我的标题",
                body = "我的版本",
                revision = "2",
                dirty = true,
                localUpdatedAt = 3,
            ),
        )

        server.enqueue(json(SESSION))
        server.enqueue(
            json(
                """
                {"error":{"code":"conflict","message":"this item changed since it was loaded"},
                 "conflict":{"expected_revision":"2","actual_revision":"5",
                   "theirs":${item("srv-1", "电脑上的标题", "服务器版本", revision = "5")}}}
                """.trimIndent(),
            ).setResponseCode(409).setHeader("ETag", "\"5\""),
        )
        server.enqueue(page())
        server.enqueue(deletions())

        val report = SyncEngine(dao, settings).sync()

        assertEquals(1, report.conflicts)
        val row = dao.byId("srv-1")!!
        // Nothing is thrown away: the local text stays, the other side is filed
        // beside it, and the row stops being retried until the author chooses.
        assertEquals("我的版本", row.body)
        assertEquals("服务器版本", row.conflictBody)
        assertEquals("5", row.conflictRevision)
        assertFalse(row.dirty)
        assertTrue(row.hasConflict)
    }

    @Test
    fun `a listing carries no text, so a pull keeps the text it already has`() = runBlocking {
        dao.upsert(
            ContentEntity(
                id = "srv-1",
                title = "旧标题",
                body = "旧正文",
                revision = "1",
                dirty = false,
                updatedAt = "2026-01-01T00:00:00Z",
                localUpdatedAt = 4,
            ),
        )

        server.enqueue(json(SESSION))
        server.enqueue(
            page(summary("srv-1", "远端标题", revision = "2", updatedAt = "2026-01-02T00:00:00Z")),
        )
        server.enqueue(deletions())

        val report = SyncEngine(dao, settings).sync()

        assertEquals(1, report.pulled)
        val row = dao.byId("srv-1")!!
        // The server said what changed; it did not send the article, and the
        // text already here is the only copy on this phone.
        assertEquals("旧正文", row.body)
        assertEquals("远端标题", row.title)
        assertEquals("2", row.revision)

        // Three requests only: no item load was needed, because the row was
        // already here.
        server.takeRequest()
        val list = server.takeRequest()
        assertEquals("/api/v1/contents", list.path?.substringBefore('?'))
        assertEquals("/api/v1/contents", server.takeRequest().path?.substringBefore('?'))
    }

    @Test
    fun `an item seen for the first time is read whole before it is stored`() = runBlocking {
        server.enqueue(json(SESSION))
        server.enqueue(page(summary("srv-2", "另一篇", revision = "1")))
        server.enqueue(
            json(item("srv-2", "另一篇", "完整正文", revision = "1")),
        )
        server.enqueue(deletions())

        val report = SyncEngine(dao, settings).sync()

        assertEquals(1, report.pulled)
        assertEquals("完整正文", dao.byId("srv-2")!!.body)

        server.takeRequest()
        server.takeRequest()
        val load = server.takeRequest()
        assertEquals("/api/v1/contents/srv-2", load.path)
    }

    @Test
    fun `a page the server moved to its trash disappears here too`() = runBlocking {
        dao.upsert(
            ContentEntity(
                id = "srv-9",
                title = "已删除",
                body = "正文",
                revision = "1",
                localUpdatedAt = 6,
            ),
        )

        server.enqueue(json(SESSION))
        server.enqueue(page())
        server.enqueue(page("""{"id":"srv-9","kind":"post","status":"draft","updated_at":"2026-01-02T00:00:00Z"}"""))

        val report = SyncEngine(dao, settings).sync()

        assertEquals(0, report.pulled)
        assertNull(dao.byId("srv-9"))

        server.takeRequest()
        server.takeRequest()
        val trash = server.takeRequest()
        assertEquals("true", trash.requestUrl?.queryParameter("deleted_only"))
    }

    @Test
    fun `a pull asks for what changed and leaves an unsent edit alone`() = runBlocking {
        settings.syncWatermark = "2026-01-01T00:00:00Z"
        dao.upsert(
            ContentEntity(
                id = "srv-1",
                title = "本地标题",
                body = "本地未上传",
                revision = "1",
                dirty = true,
                localUpdatedAt = 5,
            ),
        )

        server.enqueue(json(SESSION))
        // The upload is refused, so the edit stays local and still dirty — the
        // state in which a pull must not overwrite it.
        server.enqueue(
            json("""{"error":{"code":"internal","message":"boom"}}""").setResponseCode(500),
        )
        server.enqueue(
            page(
                summary("srv-1", "远端标题", revision = "2", updatedAt = "2026-01-02T00:00:00Z"),
                summary("srv-2", "另一篇", revision = "1", updatedAt = "2026-01-03T00:00:00Z"),
            ),
        )
        server.enqueue(
            json(item("srv-2", "另一篇", "内容", revision = "1", updatedAt = "2026-01-03T00:00:00Z")),
        )
        server.enqueue(deletions())

        val report = SyncEngine(dao, settings).sync()

        assertEquals(0, report.pushed)
        assertEquals(1, report.pulled)
        val local = dao.byId("srv-1")!!
        assertEquals("本地未上传", local.body)
        assertTrue(local.dirty)
        assertNotNull(dao.byId("srv-2"))
        // The watermark did not move past an item that was held back, so the
        // next run can still see it.
        assertEquals("2026-01-01T00:00:00Z", settings.syncWatermark)

        server.takeRequest()
        val push = server.takeRequest()
        assertEquals("PUT", push.method)
        assertEquals("/api/v1/contents/srv-1", push.path)
        val list = server.takeRequest()
        assertEquals("/api/v1/contents", list.path?.substringBefore('?'))
        assertEquals(
            "2026-01-01T00:00:00Z",
            list.requestUrl?.queryParameter("updated_from"),
        )
        assertNull(list.requestUrl?.queryParameter("include_deleted"))
    }

    private fun json(body: String): MockResponse =
        MockResponse().setResponseCode(200).setBody(body)

    /** An empty page: the pull needs one, and the trash pass needs another. */
    private fun page(vararg items: String): MockResponse = json(
        """{"items":[${items.joinToString(",")}],"has_more":false}""",
    )

    /** The trash pass. Its rows are summaries, so an id is all a test needs. */
    private fun deletions(vararg items: String): MockResponse = page(*items)

    /** One row of a listing: everything except the text. */
    private fun summary(
        id: String,
        title: String,
        revision: String,
        updatedAt: String? = "2026-01-01T00:00:00Z",
    ): String = """
        {"id":"$id","kind":"post","slug":"$id","title":"$title","status":"draft",
         "url":"/posts/$id","locator":"","revision":"$revision",
         "updated_at":"$updatedAt","pinned":false}
    """.trimIndent()

    /** One item in full, as a create, an update or an item load answers. */
    private fun item(
        id: String,
        title: String,
        body: String,
        revision: String,
        updatedAt: String? = "2026-01-01T00:00:00Z",
    ): String = """
        {"id":"$id","kind":"post","slug":"$id","title":"$title","status":"draft",
         "url":"/posts/$id","locator":"","revision":"$revision","body":"$body",
         "body_format":"markdown","updated_at":"$updatedAt","pinned":false}
    """.trimIndent()

    private companion object {
        const val SESSION = """{"required":false,"authenticated":true,"user":"ec"}"""
    }
}
