package com.kiteplus.kite.mobile.domain

import com.kiteplus.kite.mobile.data.remote.requireBody
import com.kiteplus.kite.mobile.api.model.Item
import com.kiteplus.kite.mobile.data.remote.KiteGateway
import kotlinx.coroutines.CancellationException
import retrofit2.Response

/** The item as it stands on the server, or null when it cannot be read now. */
internal suspend fun fetchItem(api: KiteGateway, id: String): Item? = try {
    api.item(id).requireBody()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

/**
 * If-Match wants the tag as the server wrote it.
 *
 * The server trims quotes itself, but a revision that came out of a failed
 * write may already be unquoted; trimming here makes both shapes safe.
 */
internal fun ifMatch(revision: String): String = revision.trim('"')

/** The revision a refusal carried in its ETag, without the quotes. */
internal fun etagOf(response: Response<*>): String? =
    response.headers()["ETag"]?.trim('"')?.takeIf { it.isNotBlank() }
