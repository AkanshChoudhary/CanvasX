package com.my_app.art_collab.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class HistoryActionType {
    ADD_LAYER,
    DELETE_LAYER,
    UPDATE_LAYER,
    REORDER_LAYERS,
    UPDATE_TRANSFORM,
    ADD_EFFECT,
    UPDATE_EFFECT,
    REMOVE_EFFECT,
    UPDATE_BLEND_MODE,
    UPDATE_OPACITY,
    MERGE_LAYERS
}

@Serializable
data class HistoryEntry(
    val id: String,
    val canvasId: String,
    val timestamp: Long,
    val actionType: HistoryActionType,
    val layersBefore: String,
    val layersAfter: String
)
