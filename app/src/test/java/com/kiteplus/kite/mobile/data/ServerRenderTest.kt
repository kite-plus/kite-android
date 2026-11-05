package com.kiteplus.kite.mobile.data

import com.kiteplus.kite.mobile.api.model.Draft
import com.kiteplus.kite.mobile.data.remote.CookieStore
import com.kiteplus.kite.mobile.data.remote.KiteGateway
import com.kiteplus.kite.mobile.data.remote.PersistentCookieJar
import com.kiteplus.kite.mobile.domain.SyncSettings
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The preview is the server's rendering, not ours — so what is pinned here is
 * that the draft goes out as the server's own Draft, that the item's id travels
 * when there is one (its images resolve against it), and that the HTML comes
 * back whole.
 */
class ServerRenderTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun render(): ServerRender {
        val base = server.url("/").toString()
        val settings = object : SyncSettings {
            override fun api(): KiteGateway = KiteGateway.create(
                base,
                PersistentCookieJar(
                    object : CookieStore {
                        private var values: List<String> = emptyList()
                        override fun read(): List<String> = values
                        override fun write(values: List<String>) {
                            this.values = values
                        }
                    },
                ),
            )
            override var syncWatermark: String? = null
            override var contentTypesJson: String? = null
            override var defaultKind: String? = "post"
        }
        return ServerRender(settings)
    }

    @Test
    fun `a preview posts the draft and keeps the whole page`() = runBlocking {
        val html = "<!doctype html><html><body><h1>你好</h1></body></html>"
        server.enqueue(MockResponse().setResponseCode(200).setBody(html))

        val out = render().preview(
            Draft(
                kind = "post",
                title = "标题",
                status = "draft",
                body = "## 你好",
            ),
            id = "srv-1",
        )

        assertEquals(html, out)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/preview?id=srv-1", request.path)
        val sent = request.body.readUtf8()
        assertTrue(sent, sent.contains("\"title\":\"标题\""))
        // The server refuses unknown fields, so nothing of ours may ride along.
        assertTrue(sent, !sent.contains("revision"))
    }

    @Test
    fun `the word count comes from the renderer, not from us`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"words":42}"""))

        val words = render().wordCount(
            Draft(kind = "post", title = "标题", status = "draft", body = "一二三"),
            id = "srv-9",
        )

        assertEquals(42, words)
        assertEquals("/api/v1/wordcount?id=srv-9", server.takeRequest().path)
    }

    @Test
    fun `a draft that has no id yet asks without one`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html></html>"))

        render().preview(Draft(kind = "post", title = "新文章", status = "draft", body = ""), id = null)

        assertEquals("/api/v1/preview", server.takeRequest().path)
    }
}
