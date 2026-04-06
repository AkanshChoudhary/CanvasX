package com.my_app.art_collab.domain.usecase.canvas

import com.my_app.art_collab.domain.model.Layer
import com.my_app.art_collab.domain.model.LayerOp
import com.my_app.art_collab.domain.repository.RealtimeDBRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FetchLayersUseCase @Inject constructor(
    private val realtimeDBRepository: RealtimeDBRepository
) {
    suspend operator fun invoke(canvasId: String): List<Layer> {
        return realtimeDBRepository.fetchLayers(canvasId)
    }

    fun observeOps(canvasId: String, since: Long): Flow<LayerOp> {
        return realtimeDBRepository.observeOps(canvasId, since)
    }
}
