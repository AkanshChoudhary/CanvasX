package com.my_app.art_collab.domain.usecase.canvas

import com.my_app.art_collab.domain.model.Canvas
import com.my_app.art_collab.domain.repository.CanvasRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCanvasListUseCase @Inject constructor(
    private val canvasRepository: CanvasRepository
) {
    operator fun invoke(): Flow<List<Canvas>> {
        return canvasRepository.observeAllCanvases()
    }
}

