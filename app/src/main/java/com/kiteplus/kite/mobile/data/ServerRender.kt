package com.kiteplus.kite.mobile.data

import com.kiteplus.kite.mobile.api.model.Draft
import com.kiteplus.kite.mobile.data.remote.ApiFailure
import com.kiteplus.kite.mobile.data.remote.failure
import com.kiteplus.kite.mobile.domain.SyncSettings

private const val NO_ADDRESS = "还没填写服务端地址"

/**
 * Preview and word count by asking the server, not by rendering markdown here.
 *
 * The admin page does the same thing, and for the reason the server's own
 * handler gives: a client with its own markdown library drifts from the build
 * on footnotes, raw html and code highlighting — and "the preview does not
 * match the site" is a complaint with no end. What comes back is the real theme
 * applied to bytes that are not on disk yet.
 */
class ServerRender(private val settings: SyncSettings) {

    /**
     * @param id the item on the server, when it has one: the preview then
     *   resolves the item's own images the way its page will.
     */
    suspend fun preview(draft: Draft, id: String?): String {
        val api = settings.api() ?: throw ApiFailure(0, NO_ADDRESS)
        val response = api.preview(draft, id)
        if (!response.isSuccessful) {
            throw failure(response.code(), response.errorBody()?.string().orEmpty())
        }
        return response.body().orEmpty()
    }

    /**
     * The number the page will print. Counting the markdown here would be a
     * second reading of it, and would show a number the site does not.
     */
    suspend fun wordCount(draft: Draft, id: String?): Int? {
        val api = settings.api() ?: return null
        val response = api.wordCount(draft, id)
        if (!response.isSuccessful) return null
        return response.body()?.words
    }
}
