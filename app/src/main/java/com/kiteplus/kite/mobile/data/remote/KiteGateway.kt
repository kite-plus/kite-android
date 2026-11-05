package com.kiteplus.kite.mobile.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.kiteplus.kite.mobile.api.endpoint.DefaultApi
import com.kiteplus.kite.mobile.api.model.ContentTypeList
import com.kiteplus.kite.mobile.api.model.Credentials
import com.kiteplus.kite.mobile.api.model.Draft
import com.kiteplus.kite.mobile.api.model.Item
import com.kiteplus.kite.mobile.api.model.Media
import com.kiteplus.kite.mobile.api.model.SessionInfo
import com.kiteplus.kite.mobile.api.model.SetupState
import com.kiteplus.kite.mobile.api.model.SummaryList
import com.kiteplus.kite.mobile.api.model.WordCount
import okhttp3.CookieJar
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

/** What went wrong, in terms the sync engine can branch on. */
class ApiFailure(
    val status: Int,
    message: String,
    /** Present on a 409: the server's version of the same item. */
    val conflict: ConflictView? = null,
    /** The revision the refusal carried, even when the body could not be read. */
    val actualRevision: String? = null,
) : Exception(message) {

    /** The session is gone and has to be re-established before retrying. */
    val isUnauthenticated: Boolean get() = status == 401

    /** This deployment is read only; nothing local will ever be accepted. */
    val isReadOnly: Boolean get() = status == 405
}

/** The two facts a 409 body carries that the client acts on. */
class ConflictView(
    val expectedRevision: String,
    val actualRevision: String,
    val theirs: Item?,
)

/**
 * The one seam between this app and the server.
 *
 * The request shapes come from the generated client, which comes from the spec
 * the server publishes — so a field or endpoint the server gains shows up here
 * after a regenerate instead of after somebody re-reads the Go source. This
 * class adds only what the generator cannot know: which call belongs to which
 * use, the API version prefix, the session cookie, and how a refusal becomes an
 * [ApiFailure].
 */
class KiteGateway internal constructor(private val api: DefaultApi) {

    /**
     * Whether the server still needs its first account set up. The one call that needs no session.
     */
    suspend fun setup(): Response<SetupState> = api.getSetupState()

    /**
     * The session as the server sees it, used to notice an expired login before a write is attempted.
     */
    suspend fun session(): Response<SessionInfo> = api.getSession()

    /**
     * Signs in and stores the session cookie for every later call.
     */
    suspend fun login(user: String, password: String, remember: Boolean): Response<SessionInfo> =
        api.login(Credentials(user = user, password = password, remember = remember))

    /**
     * Ends the session on the server.
     */
    suspend fun logout(): Response<Unit> = api.logout()

    /**
     * The content types this site declares.
     */
    suspend fun contentTypes(): Response<ContentTypeList> = api.listContentTypes()

    /**
     * One page of changed items.
     *
     * `deletedOnly` is generated as a string because the spec declares the
     * boolean query parameter in a way the template reads as a scalar; "true"
     * and null are what the server actually receives.
     */
    suspend fun list(
        limit: Int = PAGE_SIZE,
        cursor: String? = null,
        updatedFrom: String? = null,
        deletedOnly: Boolean = false,
    ): Response<SummaryList> = api.listContents(
        limit = limit,
        cursor = cursor,
        updatedFrom = updatedFrom,
        deletedOnly = if (deletedOnly) "true" else null,
    )

    /**
     * One article in full. The listing does not carry the text, this is how it is read.
     */
    suspend fun item(id: String): Response<Item> = api.getContent(id)

    /**
     * Creates an article. A create must not send If-Match; the revision comes back in the answer.
     */
    suspend fun create(draft: Draft): Response<Item> = api.createContent(draft)

    /**
     * Replaces an article, guarded by the revision the edit was made against.
     */
    suspend fun update(id: String, ifMatch: String, draft: Draft): Response<Item> =
        api.updateContent(id, ifMatch, draft)

    /**
     * Moves an article to the server's trash, also guarded by the revision.
     */
    suspend fun delete(id: String, ifMatch: String): Response<Unit> = api.deleteContent(id, ifMatch)

    /**
     * Renders a draft as a page. The body is HTML, not JSON.
     */
    suspend fun preview(draft: Draft, id: String?): Response<String> = api.preview(draft, id)

    /**
     * The word count the rendered page will print.
     */
    suspend fun wordCount(draft: Draft, id: String?): Response<WordCount> = api.countWords(draft, id)

    /**
     * Sends one file to an article's media folder.
     */
    suspend fun uploadMedia(id: String, part: MultipartBody.Part): Response<Media> =
        api.uploadMedia(id, part)

    companion object {
        private const val PAGE_SIZE = 100

        /**
         * No `serializeNulls`: an absent field has to stay absent, both because
         * the server refuses unknown properties and because `published_at: null`
         * is not a time it will accept.
         */
        val gson: Gson = GsonBuilder().create()

        /**
         * @param rootUrl where Kite listens, e.g. `http://192.168.1.20:1717`.
         *   The `/api/v1/` prefix is added here so nothing else thinks about it.
         */
        fun create(
            rootUrl: String,
            cookieJar: CookieJar,
            httpLog: Boolean = false,
        ): KiteGateway {
            val base = rootUrl.trimEnd('/') + "/api/v1/"

            val client = OkHttpClient.Builder()
                .cookieJar(cookieJar)
                // The phone talks to a machine on the same network, and the
                // read timeouts that suit a CDN are far too short for a cold
                // index.
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .apply {
                    if (httpLog) {
                        addInterceptor(
                            HttpLoggingInterceptor().apply {
                                level = HttpLoggingInterceptor.Level.BASIC
                            },
                        )
                    }
                }
                .build()

            val api = Retrofit.Builder()
                .baseUrl(base)
                .client(client)
                // Scalars first: the preview endpoint answers with a whole HTML
                // page, which Gson would try to read as JSON.
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(DefaultApi::class.java)

            return KiteGateway(api)
        }
    }
}

/** Turns a response into its body, or throws the typed failure. */
fun <T> Response<T>.requireBody(): T {
    if (isSuccessful) return body() ?: throw ApiFailure(code(), "the server sent no body")
    val text = errorBody()?.string().orEmpty()
    throw failure(code(), text, headers()["ETag"])
}

/** The failure a refusal amounts to, including what a 409 says to merge with. */
fun failure(status: Int, text: String, etag: String? = null): ApiFailure {
    val body = runCatching {
        KiteGateway.gson.fromJson(JsonParser.parseString(text).asJsonObject, ConflictBody::class.java)
    }.getOrNull()
    val message = body?.error?.message?.takeIf { it.isNotBlank() }
        ?: text.take(200).ifBlank { "HTTP $status" }
    val conflict = body?.conflict?.let { c ->
        ConflictView(
            expectedRevision = c.expectedRevision,
            actualRevision = c.actualRevision,
            theirs = c.theirs,
        )
    }
    return ApiFailure(status, message, conflict, etag?.trim('"')?.takeIf { it.isNotBlank() })
}

private class ConflictBody(
    val error: ErrorDetail? = null,
    val conflict: ConflictPart? = null,
)

private class ErrorDetail(val code: String = "", val message: String = "")

private class ConflictPart(
    @com.google.gson.annotations.SerializedName("expected_revision")
    val expectedRevision: String = "",
    @com.google.gson.annotations.SerializedName("actual_revision")
    val actualRevision: String = "",
    val theirs: Item? = null,
)
