package com.kiteplus.kite.mobile.data

import com.kiteplus.kite.mobile.data.remote.ApiFailure
import com.kiteplus.kite.mobile.data.remote.KiteGateway
import com.kiteplus.kite.mobile.domain.SyncSettings
import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The upload has to look the way the server reads it: multipart, field name
 * `file`, filename kept — the extension is what decides whether the server
 * accepts it at all.
 */
class MediaUploaderTest {

    private lateinit var server: MockWebServer

    private class TestSettings(private val api: KiteGateway) : SyncSettings {
        override fun api(): KiteGateway = api
        override var syncWatermark: String? = null
        override var contentTypesJson: String? = null
        override var defaultKind: String? = "post"
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun uploader(): MediaUploader {
        val api = KiteGateway.create(server.url("/").toString().trimEnd('/'), CookieJar.NO_COOKIES)
        return MediaUploader(TestSettings(api))
    }

    @Test
    fun `a picture goes up as multipart under the field the server reads`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"name":"px.png","path":"content/posts/a/px.png","url":"/posts/a/px.png","link":"px.png","size":4,"type":"image/png"}"""),
        )

        val media = uploader().upload("srv-1", "px.png", "image/png", byteArrayOf(1, 2, 3, 4))

        assertEquals("px.png", media.link)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/contents/srv-1/media", request.path)
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("""name="file""""))
        assertTrue(body, body.contains("""filename="px.png""""))
    }

    @Test
    fun `a kind the server does not host comes back as a refusal`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(415)
                .setBody("""{"error":{"code":"invalid_request","message":"this kind of file cannot be uploaded: .exe"}}"""),
        )

        val failure = runCatching {
            uploader().upload("srv-1", "x.exe", "application/octet-stream", byteArrayOf(1))
        }.exceptionOrNull()

        assertTrue(failure is ApiFailure)
        assertEquals(415, (failure as ApiFailure).status)
        assertTrue(failure.message!!.contains("cannot be uploaded"))
    }
}
