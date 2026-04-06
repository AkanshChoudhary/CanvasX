package com.my_app.art_collab.domain.repository

import android.net.Uri

interface StorageRepository {
    suspend fun uploadImage(canvasId: String, layerId: String, imageUri: Uri): String
    suspend fun deleteImage(canvasId: String, layerId: String)
    /** Deletes all objects under `canvases/{canvasId}/` (recursive). */
    suspend fun deleteAllCanvasFiles(canvasId: String)
}