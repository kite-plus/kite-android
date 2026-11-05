package com.kiteplus.kite.mobile.api

import com.kiteplus.kite.mobile.api.endpoint.DefaultApi
import com.kiteplus.kite.mobile.api.infrastructure.ApiClient
import com.kiteplus.kite.mobile.api.model.Draft
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * The point of M0: prove the code generated from api/openapi.json is not just
 * compilable but able to speak to a server — right paths, right query names,
 * the mandatory If-Match header, and bodies that parse into the models.
 */
class GeneratedClientTest {
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

    private fun api(): DefaultApi =
        ApiClient(baseUrl = server.url("/api/v1/").toString())
            .createService(DefaultApi::class.java)

    @Test
    fun `a listing asks for what changed and parses the page`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items":[{"id":"srv-1","kind":"post","slug":"hello","title":"标题",
                "status":"draft","url":"/posts/hello","revision":"sha256:aa"}],
                "has_more":false}""",
            ),
        )

        val page = api().listContents(limit = 3, updatedFrom = "2026-01-01T00:00:00Z")
            .body()

        assertNotNull(page)
        assertEquals("srv-1", page!!.items.single().id)
        assertEquals("sha256:aa", page.items.single().revision)

        val request = server.takeRequest()
        assertEquals("/api/v1/contents", request.path?.substringBefore('?'))
        val url = request.requestUrl!!
        assertEquals("3", url.queryParameter("limit"))
        assertEquals("2026-01-01T00:00:00Z", url.queryParameter("updated_from"))
    }

    @Test
    fun `an edit carries the revision as If-Match`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":"srv-1","kind":"post","slug":"hello","title":"改过的",
                "status":"draft","url":"/posts/hello","revision":"sha256:bb",
                "body":"正文","body_format":"markdown"}""",
            ),
        )

        val updated = api().updateContent(
            id = "srv-1",
            ifMatch = "\"sha256:aa\"",
            draft = Draft(kind = "post", title = "改过的", status = "draft", body = "正文"),
        ).body()

        assertEquals("sha256:bb", updated?.revision)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/contents/srv-1", request.path)
        assertEquals("\"sha256:aa\"", request.getHeader("If-Match"))
        // The draft is exactly the ten fields the server accepts.
        val sent = request.body.readUtf8()
        assertEquals("post", Regex("\"kind\":\"([^\"]+)\"").find(sent)?.groupValues?.get(1))
        assertEquals(false, sent.contains("revision"))
    }
}
