package com.my_app.art_collab.domain.usecase.canvas

import android.net.Uri
import com.my_app.art_collab.domain.repository.StorageRepository
import java.io.File
import javax.inject.Inject

/**
 * AI layers (and any save under app filesDir) use a plain absolute path like `/data/.../ai.png`.
 * That must become a `file://` [Uri] for [FirebaseStorage.putFile]; [Uri.parse] on a bare path
 * has no scheme and upload fails silently in the editor — collaborators never get a `layer_add`.
 */
class UploadLayerImageUseCase @Inject constructor(private val storageRepository: StorageRepository) {
    suspend operator fun invoke(canvasId: String, layerId: String, localPath: String): String {
        val uri = uriForLocalImage(localPath)
        return storageRepository.uploadImage(canvasId, layerId, uri)
    }

    private fun uriForLocalImage(localPath: String): Uri = when {
        localPath.startsWith("content://") -> Uri.parse(localPath)
        localPath.startsWith("file://") -> Uri.parse(localPath)
        else -> {
            val file = File(localPath)
            check(file.isFile) { "Image file not found: $localPath" }
            Uri.parse("file://${file.absolutePath}")
        }
    }
}