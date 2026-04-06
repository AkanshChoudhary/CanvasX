package com.my_app.art_collab.domain.repository

import com.my_app.art_collab.domain.model.Canvas
import kotlinx.coroutines.flow.Flow

interface CanvasRepository {
    fun observeAllCanvases(): Flow<List<Canvas>>
    suspend fun getCanvas(canvasId: String): Canvas?
    suspend fun createCanvas(canvas: Canvas)
    suspend fun updateCanvas(canvas: Canvas)
    suspend fun deleteCanvas(canvas: Canvas)
    suspend fun updatePinned(canvasId: String, isPinned: Boolean)
    suspend fun renameCanvas(canvasId: String, newName: String)
    suspend fun joinCanvasByCode(shareCode: String, userId: String): Canvas

    fun syncCanvasesFromRemote()
}

