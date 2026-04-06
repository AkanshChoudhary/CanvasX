package com.my_app.art_collab.domain.repository

import com.my_app.art_collab.domain.model.Layer
import com.my_app.art_collab.domain.model.LayerOp
import kotlinx.coroutines.flow.Flow

interface RealtimeDBRepository {
    suspend fun pushOpWithSnapshot(canvasId: String, op: LayerOp, snapshotUpdate: Map<String, Any?>)
    fun buildLayerDataMap(canvasId: String, layer: Layer, blobUrl: String?): Map<String, Any?>
    suspend fun fetchLayers(canvasId: String): List<Layer>
    fun observeOps(canvasId: String, since: Long): Flow<LayerOp>
    /** Removes `canvases/{canvasId}` (layers + ops + any other children). */
    suspend fun deleteCanvasSubtree(canvasId: String)
}