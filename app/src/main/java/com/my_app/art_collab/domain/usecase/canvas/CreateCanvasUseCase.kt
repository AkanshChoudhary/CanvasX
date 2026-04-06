package com.my_app.art_collab.domain.usecase.canvas

import com.my_app.art_collab.domain.model.Canvas
import com.my_app.art_collab.domain.repository.CanvasRepository
import javax.inject.Inject

class CreateCanvasUseCase @Inject constructor(
    private val canvasRepository: CanvasRepository
) {
    suspend operator fun invoke(canvas: Canvas) {
        canvasRepository.createCanvas(canvas)
    }
}

