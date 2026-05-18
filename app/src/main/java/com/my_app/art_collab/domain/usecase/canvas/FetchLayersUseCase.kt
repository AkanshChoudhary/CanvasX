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

    suspend fun pruneOpsToLimit(canvasId: String, keepTarget: Int) {
        realtimeDBRepository.pruneOpsToLimit(canvasId, keepTarget)
    }

    suspend fun isOwnerOnline(canvasId: String): Boolean {
        return realtimeDBRepository.isOwnerOnline(canvasId)
    }

    suspend fun updateSessionOnlineFlags(canvasId: String, isOwner: Boolean, online: Boolean) {
        realtimeDBRepository.updateSessionOnlineFlags(canvasId, isOwner, online)
    }

    suspend fun clearOpsIfNoOneOnline(canvasId: String) {
        realtimeDBRepository.clearOpsIfNoOneOnline(canvasId)
    }
}
