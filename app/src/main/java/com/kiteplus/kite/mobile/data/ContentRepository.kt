package com.kiteplus.kite.mobile.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kiteplus.kite.mobile.data.local.ContentDao
import com.kiteplus.kite.mobile.data.local.ContentEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** What the editor changed. Anything left null keeps the stored value. */
data class DraftPatch(
    val title: String? = null,
    val body: String? = null,
    val slug: String? = null,
    val status: String? = null,
    val locale: String? = null,
    /** A new publish date, as RFC 3339; null leaves the date alone. */
    val publishedAt: String? = null,
    val taxonomies: Map<String, List<String>>? = null,
    val meta: JsonObject? = null,
)

/**
 * Every edit the UI makes goes through here.
 *
 * Each one is written to the database and marked dirty, never sent from the
 * screen: an article typed in a tunnel has to be saved before it can be sent,
 * not after. The sync engine is the only thing that talks upward.
 */
class ContentRepository(
    private val dao: ContentDao,
    private val clock: () -> Long = System::currentTimeMillis,
    /**
     * The types the site declares, so a new article starts the way the admin
     * page starts one: the site's first kind, its default terms, its field
     * defaults.
     */
    private val kinds: () -> List<KindInfo> = { emptyList() },
    private val defaultKind: () -> String = { "post" },
) {

    /**
     * Every article this phone knows, ready to be collected by a screen.
     */
    fun observeList(): Flow<List<ContentEntity>> = dao.observeVisible()

    /**
     * One article, following it as the editor saves.
     */
    fun observeOne(id: String): Flow<ContentEntity?> = dao.observeById(id)

    /**
     * The row as it stands right now, for code that is about to edit it.
     */
    suspend fun load(id: String): ContentEntity? = dao.byId(id)

    /** The type of an item, or of a new item when [kind] is null. */
    fun typeOf(kind: String?): KindInfo? {
        val wanted = kind ?: defaultKind()
        return kinds().firstOrNull { it.kind == wanted }
    }

    /** A new article, saved locally under a placeholder id. */
    suspend fun createDraft(): String {
        val now = clock()
        val localId = LOCAL_PREFIX + UUID.randomUUID()
        val kind = defaultKind()
        val type = kinds().firstOrNull { it.kind == kind }

        dao.upsert(
            ContentEntity(
                id = localId,
                kind = kind,
                title = "",
                status = "draft",
                body = "",
                revision = "",
                taxonomiesJson = type?.startTerms()?.let { termsToJson(it) }?.toString(),
                metaJson = type?.let { metaDefaults(it) }?.let { metaToJson(it) }?.toString(),
                dirty = true,
                isNew = true,
                localUpdatedAt = now,
            ),
        )
        return localId
    }

    /** Applies an edit of any part of the item, not just its text. */
    suspend fun saveDraft(id: String, patch: DraftPatch) {
        val row = dao.byId(id) ?: return
        val status = patch.status ?: row.status
        // A date only means something while the item is scheduled; dropping the
        // status drops the date with it.
        val publishedAt = patch.publishedAt ?: if (status == "scheduled") row.publishedAt else null
        val taxonomies = patch.taxonomies?.let { termsToJson(it)?.toString() } ?: row.taxonomiesJson
        // The editor only knows the fields this content type declares; the rest
        // of what an article's frontmatter holds (layouts, menus, anything a
        // plugin wrote) is not on screen and must survive the round trip.
        val meta = mergeMeta(row.metaJson, patch.meta)
        val next = row.copy(
            title = patch.title ?: row.title,
            body = patch.body ?: row.body,
            slug = patch.slug ?: row.slug,
            status = status,
            locale = patch.locale ?: row.locale,
            publishedAt = publishedAt,
            taxonomiesJson = taxonomies,
            metaJson = meta,
        )
        // Opening an article is not an edit. The form re-submits what it was
        // given, and treating that as a change would queue an upload carrying
        // nothing and label the row "waiting to upload" for work nobody did.
        if (next.title == row.title &&
            next.body == row.body &&
            next.slug == row.slug &&
            next.status == row.status &&
            next.locale == row.locale &&
            next.publishedAt == row.publishedAt &&
            sameJson(next.taxonomiesJson, row.taxonomiesJson) &&
            sameJson(next.metaJson, row.metaJson)
        ) {
            return
        }
        dao.upsert(next.copy(dirty = true, localUpdatedAt = clock()))
    }

    /**
     * Deleting is a mark, not a removal, until the server has agreed: a
     * deletion that vanished locally would otherwise come back on the next pull.
     */
    suspend fun delete(id: String) {
        val row = dao.byId(id) ?: return
        if (row.isNew) {
            // Never reached the server; nothing to undo there.
            dao.delete(id)
            return
        }
        dao.upsert(row.copy(deletedLocally = true, dirty = true, localUpdatedAt = clock()))
    }

    /**
     * Keeps the local text and accepts that the server has moved on: the stored
     * revision becomes the one the next write has to match.
     */
    suspend fun keepLocal(id: String) {
        val row = dao.byId(id) ?: return
        dao.upsert(
            row.copy(
                revision = row.conflictRevision ?: row.revision,
                dirty = true,
                conflictBody = null,
                conflictTitle = null,
                conflictRevision = null,
                localUpdatedAt = clock(),
            ),
        )
    }

    /** Discards the local text in favour of the version the server holds. */
    suspend fun keepTheirs(id: String) {
        val row = dao.byId(id) ?: return
        dao.upsert(
            row.copy(
                body = row.conflictBody ?: row.body,
                title = row.conflictTitle ?: row.title,
                revision = row.conflictRevision ?: row.revision,
                dirty = false,
                conflictBody = null,
                conflictTitle = null,
                conflictRevision = null,
                localUpdatedAt = clock(),
            ),
        )
    }

    companion object {
        /** Marks an id the server has not issued yet. */
        const val LOCAL_PREFIX = "local:"

        /**
         * The stored taxonomy map, or an empty one when the row has none.
         */
        fun termsOf(row: ContentEntity): Map<String, List<String>> {
            val raw = row.taxonomiesJson?.takeIf { it.isNotBlank() } ?: return emptyMap()
            return runCatching {
                val obj = JsonParser.parseString(raw).asJsonObject
                obj.entrySet().mapNotNull { (key, value) ->
                    val terms = value.asJsonArray.mapNotNull { it.asString }.filter { it.isNotBlank() }
                    if (terms.isEmpty()) null else key to terms
                }.toMap()
            }.getOrDefault(emptyMap())
        }

        /**
         * The stored metadata object, or an empty one when the row has none.
         */
        fun metaOf(row: ContentEntity): JsonObject {
            val raw = row.metaJson?.takeIf { it.isNotBlank() } ?: return JsonObject()
            return runCatching { JsonParser.parseString(raw).asJsonObject }.getOrDefault(JsonObject())
        }

        private fun termsToJson(terms: Map<String, List<String>>): JsonObject? {
            val kept = terms.filterValues { it.isNotEmpty() }
            if (kept.isEmpty()) return null
            return JsonObject().apply {
                kept.forEach { (key, values) ->
                    add(key, com.google.gson.JsonArray().apply { values.forEach { add(it) } })
                }
            }
        }

        /** The value each field starts with, as the admin form fills it in. */
        private fun metaDefaults(type: KindInfo): Map<String, String> {
            val out = mutableMapOf<String, String>()
            type.fields.forEach { field ->
                val value = when (val raw = field.default) {
                    is Boolean -> raw.toString()
                    is Number -> raw.toString()
                    is String -> raw
                    else -> null
                } ?: return@forEach
                out[field.key] = value
            }
            return out
        }

        private fun metaToJson(values: Map<String, String>): JsonObject? {
            if (values.isEmpty()) return null
            return JsonObject().apply {
                values.forEach { (key, value) ->
                    when (value) {
                        "true", "false" -> addProperty(key, value.toBoolean())
                        else -> addProperty(key, value)
                    }
                }
            }
        }
    }
}


/** Two stored JSON columns are the same value when they parse to the same tree. */
private fun sameJson(a: String?, b: String?): Boolean {
    if (a.isNullOrBlank() || b.isNullOrBlank()) return a.isNullOrBlank() && b.isNullOrBlank()
    if (a == b) return true
    return runCatching { JsonParser.parseString(a) == JsonParser.parseString(b) }.getOrDefault(false)
}

/**
 * The stored metadata with the form's values laid over it.
 *
 * Replacing it outright would delete every key the editor does not show — a
 * layout choice, a plugin's field — the first time an article was opened from
 * the phone.
 */
private fun mergeMeta(stored: String?, patched: JsonObject?): String? {
    if (patched == null) return stored
    if (patched.size() == 0) return stored
    val base = runCatching { JsonParser.parseString(stored.orEmpty()) }.getOrNull()
    if (base == null || !base.isJsonObject) return patched.toString()
    val out = base.asJsonObject.deepCopy()
    patched.entrySet().forEach { (key, value) ->
        if (!value.isJsonNull) out.add(key, value)
    }
    return out.toString()
}
