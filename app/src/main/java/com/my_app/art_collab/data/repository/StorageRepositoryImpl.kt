package com.my_app.art_collab.data.repository

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.my_app.art_collab.debug.LayerImageDebug
import com.my_app.art_collab.domain.repository.StorageRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class StorageRepositoryImpl @Inject constructor(
    private val storage: FirebaseStorage
) : StorageRepository {
    override suspend fun uploadImage(canvasId: String, layerId: String, imageUri: Uri): String {
        val storageRef = storage.reference.child("canvases/$canvasId/layers/${layerId}.png")

        storageRef.putFile(imageUri).await()

        return storageRef.downloadUrl.await().toString()
    }

    override suspend fun deleteImage(canvasId: String, layerId: String) {
        try {
            val storageRef = storage.reference.child("canvases/$canvasId/layers/${layerId}.png")
            storageRef.delete().await()
        } catch (e: Exception) {
            LayerImageDebug.e(TAG, "deleteImage failed canvasId=$canvasId layerId=$layerId", e)
        }
    }

    override suspend fun deleteAllCanvasFiles(canvasId: String) {
        val root = storage.reference.child("canvases/$canvasId")
        try {
            deleteStorageTree(root)
        } catch (e: Exception) {
            LayerImageDebug.e(TAG, "deleteAllCanvasFiles failed canvasId=$canvasId", e)
        }
    }

    private suspend fun deleteStorageTree(ref: StorageReference) {
        val list = ref.listAll().await()
        list.items.forEach { item ->
            try {
                item.delete().await()
            } catch (e: Exception) {
                LayerImageDebug.e(TAG, "deleteStorageTree item failed: ${item.path}", e)
            }
        }
        list.prefixes.forEach { prefix ->
            deleteStorageTree(prefix)
        }
    }

    private companion object {
        const val TAG = "StorageRepository"
    }
}
