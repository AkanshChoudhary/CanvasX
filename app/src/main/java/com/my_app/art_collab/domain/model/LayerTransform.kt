package com.my_app.art_collab.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LayerTransform(
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f
)