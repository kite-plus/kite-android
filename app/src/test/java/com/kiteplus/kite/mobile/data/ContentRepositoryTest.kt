package com.kiteplus.kite.mobile.data

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.kiteplus.kite.mobile.data.local.FakeContentDao
import com.kiteplus.kite.mobile.api.model.Field
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the editor writes is what the admin page writes: the site's own kind,
 * its default terms, its field defaults, and whatever the author changes
 * afterwards — address, standing, categories, fields.
 */
class ContentRepositoryTest {

    private val postType = KindInfo(
        kind = "post",
        label = "Post",
        route = "/posts/:slug",
        taxonomies = listOf("tags", "categories"),
        newTerms = mapOf("categories" to listOf("随笔")),
        fields = listOf(
            Field(key = "description", type = "text", label = "Description"),
            Field(key = "cover", type = "image", label = "Cover image"),
            Field(key = "pinned", type = "boolean", label = "Pinned", default = false),
        ),
    )

    private fun repository(dao: FakeContentDao) = ContentRepository(
        dao = dao,
        clock = { 1_000 },
        kinds = { listOf(postType) },
        defaultKind = { "post" },
    )

    @Test
    fun `a new article starts the way the site starts one`() = runBlocking {
        val dao = FakeContentDao()
        val id = repository(dao).createDraft()

        val row = dao.byId(id)!!
        assertEquals("post", row.kind)
        assertEquals("draft", row.status)
        assertTrue(row.isNew && row.dirty)
        // The site's default category and the field defaults come along, so the
        // first save does not quietly strip them.
        assertEquals(mapOf("categories" to listOf("随笔")), ContentRepository.termsOf(row))
        assertEquals(JsonPrimitive(false), ContentRepository.metaOf(row).get("pinned"))
        assertEquals("/posts/", postType.slugPrefix)
    }

    @Test
    fun `an edit writes every field the admin page offers`() = runBlocking {
        val dao = FakeContentDao()
        val repo = repository(dao)
        val id = repo.createDraft()

        val meta = JsonObject().apply {
            addProperty("description", "一句话介绍")
            addProperty("pinned", true)
        }
        repo.saveDraft(
            id,
            DraftPatch(
                title = "标题",
                body = "正文",
                slug = "hello",
                status = "published",
                taxonomies = mapOf("tags" to listOf("Go", "Kite"), "categories" to listOf("随笔")),
                meta = meta,
            ),
        )

        val row = dao.byId(id)!!
        assertEquals("标题", row.title)
        assertEquals("hello", row.slug)
        assertEquals("published", row.status)
        assertEquals(listOf("Go", "Kite"), ContentRepository.termsOf(row)["tags"])
        assertEquals("一句话介绍", ContentRepository.metaOf(row).get("description").asString)
        assertTrue(row.dirty)
    }

    @Test
    fun `opening an article and saving nothing leaves it synced`() = runBlocking {
        val dao = FakeContentDao()
        val repo = repository(dao)
        val id = repo.createDraft()
        repo.saveDraft(id, DraftPatch(title = "标题", body = "正文"))
        dao.upsert(dao.byId(id)!!.copy(dirty = false, isNew = false))

        // The editor re-submits what the form was handed; nothing changed.
        repo.saveDraft(
            id,
            DraftPatch(
                title = "标题",
                body = "正文",
                slug = "",
                status = "draft",
                taxonomies = ContentRepository.termsOf(dao.byId(id)!!),
                meta = ContentRepository.metaOf(dao.byId(id)!!),
            ),
        )

        assertEquals(false, dao.byId(id)!!.dirty)
    }

    @Test
    fun `typing one character is still an edit`() = runBlocking {
        val dao = FakeContentDao()
        val repo = repository(dao)
        val id = repo.createDraft()
        repo.saveDraft(id, DraftPatch(title = "标题", body = "正文"))
        dao.upsert(dao.byId(id)!!.copy(dirty = false, isNew = false))

        repo.saveDraft(id, DraftPatch(title = "标题", body = "正文一"))
        assertTrue(dao.byId(id)!!.dirty)
    }

    @Test
    fun `metadata the form does not show survives a save`() = runBlocking {
        val dao = FakeContentDao()
        val repo = repository(dao)
        val id = repo.createDraft()
        val stored = JsonObject().apply {
            addProperty("layout", "wide")
            addProperty("pinned", false)
        }
        dao.upsert(dao.byId(id)!!.copy(metaJson = stored.toString()))

        repo.saveDraft(
            id,
            DraftPatch(meta = JsonObject().apply { addProperty("description", "简介") }),
        )

        val meta = ContentRepository.metaOf(dao.byId(id)!!)
        assertEquals("wide", meta.get("layout").asString)
        assertEquals("简介", meta.get("description").asString)
    }

    @Test
    fun `a date only survives while the item is scheduled`() = runBlocking {
        val dao = FakeContentDao()
        val repo = repository(dao)
        val id = repo.createDraft()

        repo.saveDraft(
            id,
            DraftPatch(status = "scheduled", publishedAt = "2026-11-01T09:00:00+08:00"),
        )
        assertEquals("2026-11-01T09:00:00+08:00", dao.byId(id)!!.publishedAt)

        // Back to a draft: a publish date on an unpublished item would make the
        // next write carry a time the author did not mean.
        repo.saveDraft(id, DraftPatch(status = "draft"))
        assertNull(dao.byId(id)!!.publishedAt)
    }
}
