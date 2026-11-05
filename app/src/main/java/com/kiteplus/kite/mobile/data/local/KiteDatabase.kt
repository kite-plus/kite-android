package com.kiteplus.kite.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ContentEntity::class], version = 1, exportSchema = true)
abstract class KiteDatabase : RoomDatabase() {
    abstract fun contents(): ContentDao

    companion object {
        fun open(context: Context): KiteDatabase =
            Room.databaseBuilder(context, KiteDatabase::class.java, "kite.db")
                // Nothing here is a copy of anything irreplaceable: the server
                // holds the articles, and unsynced edits are brief by nature.
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
