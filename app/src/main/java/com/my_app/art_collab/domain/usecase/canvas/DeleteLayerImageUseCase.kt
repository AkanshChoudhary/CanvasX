package com.my_app.art_collab.domain.usecase.canvas

import android.net.Uri
import com.my_app.art_collab.domain.repository.StorageRepository
import javax.inject.Inject
import androidx.core.net.toUri

class DeleteLayerImageUseCase @Inject constructor(private val storageRepository: StorageRepository) {
    suspend operator fun invoke(canvasId: String, layerId: String){
        storageRepository.deleteImage(canvasId, layerId)
    }
}