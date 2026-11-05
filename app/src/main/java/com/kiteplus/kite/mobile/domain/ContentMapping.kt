package com.kiteplus.kite.mobile.domain

import com.google.gson.reflect.TypeToken
import com.kiteplus.kite.mobile.api.model.Draft
import com.kiteplus.kite.mobile.api.model.Item
import com.kiteplus.kite.mobile.api.model.Summary
import com.kiteplus.kite.mobile.data.local.ContentEntity
import com.kiteplus.kite.mobile.data.remote.KiteGateway
import java.lang.reflect.Type

/**
 * The one place where a database row and the server's view of it are turned
 * into each other.
 *
 * Keeping this in one object is what lets the push and pull halves stay small,
 * and it is also the seam the generated client moved: a field the server gains
 * now arrives here as a compiler error instead of a silent omission.
 */
object ContentMapping {

    private val mapType: Type = object : TypeToken<Map<String, Any>>() {}.type
    private val termsType: Type = object : TypeToken<Map<String, List<String>>>() {}.type
    private val namesType: Type = object : TypeToken<List<String>>() {}.type

    /** What this row sends: exactly the ten fields a draft may carry. */
    fun draftOf(row: ContentEntity) = Draft(
        kind = row.kind,
        title = row.title,
        // An empty slug means "let the server derive one"; sending "" would pin
        // the item to a name it never chose.
        slug = row.slug.takeIf { it.isNotBlank() },
        status = row.status,
        body = row.body,
        meta = parse(row.metaJson, mapType),
        taxonomies = parse(row.taxonomiesJson, termsType),
        aliases = parse(row.aliasesJson, namesType),
        locale = row.locale?.takeIf { it.isNotBlank() },
        publishedAt = row.publishedAt,
    )

    /** The row for an item the server described in full. */
    fun fromFull(
        item: Item,
        localUpdatedAt: Long,
        bodyFallback: String,
    ) = ContentEntity(
        id = item.id,
        kind = item.kind,
        slug = item.slug,
        title = item.title,
        status = item.status,
        locale = item.locale,
        excerpt = item.excerpt,
        taxonomiesJson = toJson(item.taxonomies),
        metaJson = toJson(item.meta),
        aliasesJson = toJson(item.aliases),
        url = item.url,
        locator = item.locator,
        revision = item.revision,
        // A create or update answer always carries the text; the fallback is
        // for a response that somehow left it out.
        body = item.body.ifBlank { bodyFallback },
        createdAt = item.createdAt,
        updatedAt = item.updatedAt,
        publishedAt = item.publishedAt,
        modifiedAt = item.modifiedAt,
        pinned = item.pinned ?: false,
        deletedAt = item.deletedAt,
        dirty = false,
        isNew = false,
        deletedLocally = false,
        localUpdatedAt = localUpdatedAt,
    )

    /**
     * The row for a listing row, keeping everything a summary does not carry.
     *
     * The text, the metadata, the aliases and the trash marker live only on the
     * item itself: a summary is the change signal, not the content.
     */
    fun fromSummary(summary: Summary, existing: ContentEntity) = existing.copy(
        kind = summary.kind,
        slug = summary.slug,
        title = summary.title,
        status = summary.status,
        locale = summary.locale,
        excerpt = summary.excerpt,
        taxonomiesJson = toJson(summary.taxonomies) ?: existing.taxonomiesJson,
        url = summary.url,
        locator = summary.locator,
        revision = summary.revision,
        createdAt = summary.createdAt ?: existing.createdAt,
        updatedAt = summary.updatedAt ?: existing.updatedAt,
        publishedAt = summary.publishedAt ?: existing.publishedAt,
        modifiedAt = summary.modifiedAt ?: existing.modifiedAt,
        pinned = summary.pinned ?: false,
        dirty = false,
        isNew = false,
        deletedLocally = false,
        localUpdatedAt = existing.localUpdatedAt,
        // A pull that brings a new version of an item holding an unresolved
        // conflict keeps the archived copy until the author decides.
        conflictRevision = existing.conflictRevision,
        conflictBody = existing.conflictBody,
        conflictTitle = existing.conflictTitle,
    )

    private fun <T> parse(raw: String?, type: Type): T? =
        raw?.takeIf { it.isNotBlank() }
            ?.let { runCatching { KiteGateway.gson.fromJson<T>(it, type) }.getOrNull() }

    private fun toJson(value: Any?): String? =
        value?.let { runCatching { KiteGateway.gson.toJson(it) }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
}
