package com.my_app.art_collab.domain.usecase.canvas

import com.my_app.art_collab.domain.repository.CanvasRepository
import javax.inject.Inject

class SyncCanvasesUseCase @Inject constructor(private val canvasRepository: CanvasRepository) {
    operator fun invoke() {
        canvasRepository.syncCanvasesFromRemote()
    }
}