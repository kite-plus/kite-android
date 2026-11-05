package com.kiteplus.kite.mobile.domain

import com.kiteplus.kite.mobile.data.local.ContentDao
import com.kiteplus.kite.mobile.data.remote.ApiFailure
import com.kiteplus.kite.mobile.data.remote.KiteGateway
import com.kiteplus.kite.mobile.data.remote.requireBody
import kotlinx.coroutines.CancellationException

/**
 * Brings down what changed on the server.
 *
 * Two passes, because one cannot do both jobs: the listing is the change signal
 * but carries no text and no trash marker, so items seen for the first time are
 * read whole, and the trash is asked for by itself.
 *
 * The watermark only moves when nothing was held back — stepping past an item
 * that was skipped would lose it for good.
 */
class Puller(
    private val dao: ContentDao,
    private val settings: SyncSettings,
) {

    suspend fun run(api: KiteGateway): SyncReport {
        val since = settings.syncWatermark ?: dao.latestUpdatedAt()
        var cursor: String? = null
        var pulled = 0
        var newest: String? = since
        var more = true
        var held = false

        try {
            while (more) {
                val page = api.list(cursor = cursor, updatedFrom = since).requireBody()

                for (summary in page.items) {
                    val existing = dao.byId(summary.id)
                    // An edit that has not been sent yet outranks what the
                    // server says; the next push run reconciles it.
                    if (existing?.dirty == true) {
                        held = true
                        continue
                    }

                    val row = if (existing == null) {
                        // A listing carries no text and no metadata: read the
                        // item itself once, or leave it for the next run rather
                        // than storing an empty article over nothing.
                        val full = fetchItem(api, summary.id)
                        if (full == null) {
                            held = true
                            continue
                        }
                        ContentMapping.fromFull(
                            full,
                            localUpdatedAt = System.currentTimeMillis(),
                            bodyFallback = "",
                        )
                    } else {
                        ContentMapping.fromSummary(summary, existing)
                    }

                    dao.upsert(row)
                    pulled++
                    summary.updatedAt?.let { stamp ->
                        val current = newest
                        if (current == null || stamp > current) newest = stamp
                    }
                }

                cursor = page.nextCursor?.takeIf { it.isNotBlank() }
                more = page.hasMore && cursor != null
            }

            // Deletions are the one thing a listing cannot show: a summary has
            // no `deleted_at` field at all.
            if (pullDeletions(api, since)) held = true

            if (!held) newest?.let { settings.syncWatermark = it }
            return SyncReport(pulled = pulled)
        } catch (e: ApiFailure) {
            return SyncReport(
                pulled = pulled,
                error = if (e.isUnauthenticated) "需要重新登录" else e.message,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SyncReport(pulled = pulled, error = e.message ?: e.toString())
        }
    }

    /**
     * Removes locally what the server moved to its trash.
     *
     * Returns true when something was held back, which keeps the watermark from
     * advancing past an item that was skipped.
     */
    private suspend fun pullDeletions(api: KiteGateway, since: String?): Boolean {
        var cursor: String? = null
        var more = true
        var held = false

        while (more) {
            val page = api.list(cursor = cursor, updatedFrom = since, deletedOnly = true).requireBody()

            for (summary in page.items) {
                val row = dao.byId(summary.id) ?: continue
                if (row.dirty) {
                    // An unsent edit still belongs to the author; deleting it
                    // here would silently drop work.
                    held = true
                    continue
                }
                dao.delete(summary.id)
            }

            cursor = page.nextCursor?.takeIf { it.isNotBlank() }
            more = page.hasMore && cursor != null
        }
        return held
    }
}
