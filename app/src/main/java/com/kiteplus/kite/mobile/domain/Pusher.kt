package com.kiteplus.kite.mobile.domain

import com.kiteplus.kite.mobile.data.local.ContentDao
import com.kiteplus.kite.mobile.data.remote.ApiFailure
import com.kiteplus.kite.mobile.data.remote.KiteGateway
import com.kiteplus.kite.mobile.data.remote.failure
import com.kiteplus.kite.mobile.data.remote.requireBody
import kotlinx.coroutines.CancellationException

/**
 * Sends what the author changed here, oldest edit first.
 *
 * Every step is written so that running it twice is harmless: a create that
 * already happened carries the server's id, an update against a revision that
 * no longer matches turns into a conflict instead of an overwrite, and a delete
 * that finds nothing is counted as done.
 */
class Pusher(
    private val dao: ContentDao,
    private val conflicts: ConflictResolver,
) {

    suspend fun run(api: KiteGateway): SyncReport {
        var pushed = 0
        var filed = 0
        var error: String? = null

        for (row in dao.pending()) {
            try {
                when {
                    // Created and deleted before it ever reached the server:
                    // there is nothing to tell the server about.
                    row.isNew && row.deletedLocally -> dao.delete(row.id)

                    row.deletedLocally -> {
                        val response = api.delete(row.id, ifMatch(row.revision))
                        when {
                            response.isSuccessful -> dao.delete(row.id)
                            // Already gone is the outcome we wanted.
                            response.code() == 404 || response.code() == 410 -> dao.delete(row.id)
                            response.code() == 428 || response.code() == 412 -> {
                                filed++
                                conflicts.file(
                                    row,
                                    fetchItem(api, row.id),
                                    fallbackRevision = etagOf(response),
                                )
                            }
                            else -> error = failure(
                                response.code(),
                                response.errorBody()?.string().orEmpty(),
                                etagOf(response),
                            ).message
                        }
                    }

                    row.isNew -> {
                        val created = api.create(ContentMapping.draftOf(row)).requireBody()
                        // The row has to take the server's id before anything
                        // else refers to it.
                        dao.renameId(row.id, created.id)
                        dao.upsert(
                            ContentMapping.fromFull(
                                created,
                                localUpdatedAt = row.localUpdatedAt,
                                bodyFallback = row.body,
                            ),
                        )
                        pushed++
                    }

                    else -> {
                        val updated = api.update(row.id, ifMatch(row.revision), ContentMapping.draftOf(row))
                            .requireBody()
                        dao.upsert(
                            ContentMapping.fromFull(
                                updated,
                                localUpdatedAt = row.localUpdatedAt,
                                bodyFallback = row.body,
                            ),
                        )
                        pushed++
                    }
                }
            } catch (e: ApiFailure) {
                if (e.isUnauthenticated) {
                    return SyncReport(pushed = pushed, conflicts = filed, error = "需要重新登录")
                }
                if (e.conflict != null) {
                    filed++
                    conflicts.file(row, e.conflict.theirs, e.actualRevision)
                } else {
                    // A refused write is not a reason to lose the text: the row
                    // stays dirty and the next run tries again.
                    error = e.message
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            }
        }
        return SyncReport(pushed = pushed, conflicts = filed, error = error)
    }
}
