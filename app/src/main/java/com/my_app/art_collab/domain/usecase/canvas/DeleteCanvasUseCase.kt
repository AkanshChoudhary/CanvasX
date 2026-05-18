package com.my_app.art_collab.domain.usecase.canvas

import com.my_app.art_collab.domain.model.Canvas
import com.my_app.art_collab.domain.repository.CanvasRepository
import com.my_app.art_collab.domain.repository.RealtimeDBRepository
import com.my_app.art_collab.domain.repository.StorageRepository
import javax.inject.Inject

class DeleteCanvasUseCase @Inject constructor(
    private val canvasRepository: CanvasRepository,
    private val realtimeDBRepository: RealtimeDBRepository,
    private val storageRepository: StorageRepository
) {
    suspend operator fun invoke(canvas: Canvas) {
        val id = canvas.id
        try {
            storageRepository.deleteAllCanvasFiles(id)
        } catch (_: Exception) {
        }
        realtimeDBRepository.deleteCanvasSubtree(id)
        canvasRepository.deleteCanvas(canvas)
    }
}

