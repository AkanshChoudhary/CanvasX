package com.my_app.art_collab.domain.usecase.canvas

import com.my_app.art_collab.domain.repository.CanvasRepository
import javax.inject.Inject

class UpdateCanvasUseCase @Inject constructor(
    private val canvasRepository: CanvasRepository
) {
    suspend fun updatePinned(canvasId: String, isPinned: Boolean) {
        canvasRepository.updatePinned(canvasId, isPinned)
    }

    suspend fun rename(canvasId: String, newName: String) {
        canvasRepository.renameCanvas(canvasId, newName)
    }
}

