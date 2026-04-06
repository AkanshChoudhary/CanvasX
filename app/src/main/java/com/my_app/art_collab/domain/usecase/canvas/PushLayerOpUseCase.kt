package com.my_app.art_collab.domain.usecase.canvas

import com.my_app.art_collab.domain.model.Layer
import com.my_app.art_collab.domain.model.LayerOp
import com.my_app.art_collab.domain.repository.RealtimeDBRepository
import javax.inject.Inject

class PushLayerOpUseCase @Inject constructor(private val realtimeDBRepository: RealtimeDBRepository) {

    suspend operator fun invoke(canvasId: String, op: LayerOp, snapshotUpdate: Map<String, Any?>) {
        realtimeDBRepository.pushOpWithSnapshot(canvasId, op, snapshotUpdate)
    }

    fun buildLayerDataMap(canvasId: String, layer: Layer, blobUrl: String?): Map<String, Any?> {
        return realtimeDBRepository.buildLayerDataMap(canvasId, layer, blobUrl)
    }
}