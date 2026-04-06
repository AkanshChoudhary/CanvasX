package com.my_app.art_collab.data.repository

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
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
            e.printStackTrace()
        }
    }

    override suspend fun deleteAllCanvasFiles(canvasId: String) {
        val root = storage.reference.child("canvases/$canvasId")
        try {
            deleteStorageTree(root)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun deleteStorageTree(ref: StorageReference) {
        val list = ref.listAll().await()
        list.items.forEach { item ->
            try {
                item.delete().await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        list.prefixes.forEach { prefix ->
            deleteStorageTree(prefix)
        }
    }
}