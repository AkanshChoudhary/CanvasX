package com.my_app.art_collab.domain.usecase.canvas

import com.my_app.art_collab.domain.model.Canvas
import com.my_app.art_collab.domain.repository.CanvasRepository
import javax.inject.Inject

class JoinCanvasUseCase @Inject constructor(
    private val canvasRepository: CanvasRepository
) {
    suspend operator fun invoke(shareCode: String, userId: String): Canvas {
        return canvasRepository.joinCanvasByCode(shareCode.trim().uppercase(), userId)
    }
}
