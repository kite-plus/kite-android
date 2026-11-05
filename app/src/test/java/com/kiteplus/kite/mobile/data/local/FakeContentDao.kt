package com.kiteplus.kite.mobile.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An in-memory stand-in for the Room DAO.
 *
 * The queries the app relies on are reproduced here rather than mocked, because
 * a test that mocks a query is testing the mock: the sync engine's decisions
 * depend on what a listing actually returns.
 */
class FakeContentDao : ContentDao {

    private val rows = LinkedHashMap<String, ContentEntity>()
    private val visibleFlow = MutableStateFlow<List<ContentEntity>>(emptyList())
    private val oneFlows = mutableMapOf<String, MutableStateFlow<ContentEntity?>>()

    private fun refresh() {
        visibleFlow.value = rows.values
            .filter { it.deletedAt == null && !it.deletedLocally }
            .sortedWith(
                compareByDescending<ContentEntity> { it.pinned }
                    .thenByDescending { it.localUpdatedAt }
                    .thenBy { it.title },
            )
        rows.forEach { (id, row) -> oneFlows.getOrPut(id) { MutableStateFlow(null) }.value = row }
    }

    override fun observeVisible(): Flow<List<ContentEntity>> = visibleFlow

    override fun observeById(id: String): Flow<ContentEntity?> =
        oneFlows.getOrPut(id) { MutableStateFlow(rows[id]) }

    override suspend fun byId(id: String): ContentEntity? = rows[id]

    override suspend fun pending(): List<ContentEntity> =
        rows.values.filter { it.dirty }.sortedBy { it.localUpdatedAt }

    override suspend fun upsert(row: ContentEntity) {
        rows[row.id] = row
        refresh()
    }

    override suspend fun upsertAll(rows: List<ContentEntity>) {
        rows.forEach { this.rows[it.id] = it }
        refresh()
    }

    override suspend fun delete(id: String) {
        rows.remove(id)
        refresh()
    }

    override suspend fun renameId(oldId: String, newId: String) {
        val row = rows.remove(oldId) ?: return
        rows[newId] = row.copy(id = newId)
        refresh()
    }

    override suspend fun latestUpdatedAt(): String? =
        rows.values.mapNotNull { it.updatedAt }.maxOrNull()

    /** Test-only convenience. */
    fun all(): List<ContentEntity> = rows.values.toList()
}
