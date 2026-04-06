package com.my_app.art_collab.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.my_app.art_collab.data.local.db.dao.CanvasDao
import com.my_app.art_collab.data.local.db.entity.CanvasEntity

@Database(
    entities = [CanvasEntity::class],
    version = 4,
    exportSchema = false
)
abstract class CanvasXDatabase : RoomDatabase() {
    abstract fun canvasDao(): CanvasDao
}
