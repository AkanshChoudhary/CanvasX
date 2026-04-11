package com.my_app.art_collab.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.my_app.art_collab.data.local.db.entity.CanvasEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CanvasDao {

    @Query("SELECT * FROM canvases ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAllCanvases(): Flow<List<CanvasEntity>>

    @Query("SELECT * FROM canvases WHERE id = :canvasId")
    suspend fun getCanvas(canvasId: String): CanvasEntity?

    @Upsert
    suspend fun upsertCanvas(canvas: CanvasEntity)

    @Delete
    suspend fun deleteCanvas(canvas: CanvasEntity)

    @Query("UPDATE canvases SET isPinned = :isPinned WHERE id = :canvasId")
    suspend fun updatePinned(canvasId: String, isPinned: Boolean)

    @Query("UPDATE canvases SET name = :name, updatedAt = :updatedAt WHERE id = :canvasId")
    suspend fun updateName(canvasId: String, name: String, updatedAt: Long)

    @Query("UPDATE canvases SET thumbnailLocalPath = :path WHERE id = :canvasId")
    suspend fun updateThumbnail(canvasId: String, path: String)

    @Query("DELETE FROM canvases WHERE id NOT IN (:ids)")
    suspend fun deleteCanvasesNotIn(ids: List<String>)

    @Query("DELETE FROM canvases")
    suspend fun deleteAllCanvases()
}

