package com.kiteplus.kite.mobile.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One piece of content, as this phone knows it.
 *
 * The row is the truth the UI reads: every screen works from the database, not
 * from the network, which is what lets the editor stay usable with no
 * connection at all. Fields above [dirty] mirror what the server sent; fields
 * below it exist only here.
 */
@Entity(
    tableName = "contents",
    indices = [Index(value = ["dirty"]), Index(value = ["updatedAt"])],
)
data class ContentEntity(
    @PrimaryKey val id: String,
    val kind: String = "post",
    val slug: String = "",
    val title: String = "",
    val status: String = "draft",
    val locale: String? = null,
    val excerpt: String? = null,
    /** Taxonomies, meta and aliases travel as raw JSON so an edit cannot drop them. */
    val taxonomiesJson: String? = null,
    val metaJson: String? = null,
    val aliasesJson: String? = null,
    val url: String? = null,
    val locator: String? = null,
    /** The server's version tag, quoted or not, exactly as If-Match wants it. */
    val revision: String = "",
    val body: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val publishedAt: String? = null,
    val modifiedAt: String? = null,
    val pinned: Boolean = false,
    val deletedAt: String? = null,

    /** Set when local text differs from the last thing the server confirmed. */
    val dirty: Boolean = false,
    /** Set until a create has been accepted and the row carries a real id. */
    val isNew: Boolean = false,
    val deletedLocally: Boolean = false,
    /** Epoch millis of the last local touch; the list sorts on it. */
    val localUpdatedAt: Long = 0L,

    // A refused write keeps the local text and files the server's version
    // beside it, so nothing is lost while the two are being reconciled.
    val conflictRevision: String? = null,
    val conflictBody: String? = null,
    val conflictTitle: String? = null,
) {
    val hasConflict: Boolean get() = conflictBody != null
}
