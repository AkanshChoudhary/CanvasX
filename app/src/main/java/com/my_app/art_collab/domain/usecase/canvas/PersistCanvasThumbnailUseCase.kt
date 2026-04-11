package com.my_app.art_collab.domain.usecase.canvas

import android.graphics.Bitmap
import com.my_app.art_collab.data.image.CanvasThumbnailStore
import com.my_app.art_collab.domain.repository.CanvasRepository
import javax.inject.Inject

class PersistCanvasThumbnailUseCase @Inject constructor(
    private val thumbnailStore: CanvasThumbnailStore,
    private val canvasRepository: CanvasRepository,
) {
    suspend operator fun invoke(canvasId: String, bitmap: Bitmap) {
        val path = thumbnailStore.saveJpeg(canvasId, bitmap)
        canvasRepository.updateThumbnail(canvasId, path)
    }
}
