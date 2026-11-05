package com.kiteplus.kite.mobile.domain

import com.kiteplus.kite.mobile.data.local.ContentDao
import com.kiteplus.kite.mobile.data.local.ContentEntity
import com.kiteplus.kite.mobile.api.model.Item

/**
 * What to do when the server refused a write because the item moved on.
 *
 * Retrying would keep losing the same race, and silently overwriting either
 * side would throw work away; keeping both is the only answer that loses
 * nothing. The local text stays where it is, the server's version is filed
 * beside it, and the row stops being dirty so it is not retried until the
 * author decides.
 */
class ConflictResolver(private val dao: ContentDao) {

    /**
     * @param fallbackRevision the ETag the refusal carried. A 409 always states
     *   what the item became, but the header is what makes the next write match
     *   even when the body could not be read.
     */
    suspend fun file(row: ContentEntity, theirs: Item?, fallbackRevision: String? = null) {
        dao.upsert(
            row.copy(
                dirty = false,
                conflictRevision = theirs?.revision ?: fallbackRevision ?: row.revision,
                conflictBody = theirs?.body ?: row.conflictBody,
                conflictTitle = theirs?.title ?: row.conflictTitle,
            ),
        )
    }
}
