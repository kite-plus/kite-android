package com.kiteplus.kite.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {
    @Query(
        "SELECT * FROM contents WHERE deletedAt IS NULL AND deletedLocally = 0 " +
            "ORDER BY pinned DESC, localUpdatedAt DESC, title ASC",
    )
    /**
     * Everything that is alive here, pinned first and then most recently edited.
     */
    fun observeVisible(): Flow<List<ContentEntity>>

    @Query("SELECT * FROM contents WHERE id = :id")
    /**
     * One article as it changes, for an editor that is open on it.
     */
    fun observeById(id: String): Flow<ContentEntity?>

    @Query("SELECT * FROM contents WHERE id = :id")
    /**
     * One article as it stands, for code that is about to write over it.
     */
    suspend fun byId(id: String): ContentEntity?

    @Query("SELECT * FROM contents WHERE dirty = 1 ORDER BY localUpdatedAt ASC")
    /**
     * The rows with unsent edits, oldest first, which is the order to send them in.
     */
    suspend fun pending(): List<ContentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    /**
     * Stores one row, replacing whatever held that id.
     */
    suspend fun upsert(row: ContentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    /**
     * Stores a page of rows in one transaction.
     */
    suspend fun upsertAll(rows: List<ContentEntity>)

    @Query("DELETE FROM contents WHERE id = :id")
    /**
     * Removes the row. The article on the server is a separate question.
     */
    suspend fun delete(id: String)

    /**
     * A create gets its real id from the server. Moving the row is cheaper than
     * deleting and re-inserting it, and it keeps the open editor pointed at it.
     */
    @Query("UPDATE contents SET id = :newId WHERE id = :oldId")
    /**
     * Gives a locally created article the id the server assigned to it. Renaming is
     * cheaper than deleting and re-inserting, and it keeps an open editor pointing at
     * the same row.
     */
    suspend fun renameId(oldId: String, newId: String)

    @Query("SELECT MAX(updatedAt) FROM contents WHERE updatedAt IS NOT NULL")
    /**
     * The newest server timestamp held here, used as the watermark when none was recorded.
     */
    suspend fun latestUpdatedAt(): String?
}
