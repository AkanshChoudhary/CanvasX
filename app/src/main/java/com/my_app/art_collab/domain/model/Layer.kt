package com.my_app.art_collab.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Layer(
    val id: String,
    val canvasId: String,
    val ownerId: String,
    val name: String,
    val type: LayerType,
    val sourceBitmapPath: String? = null,
    val transform: LayerTransform = LayerTransform(),
    val effectChain: List<Effect> = emptyList(),
    val blendMode: BlendMode = BlendMode.NORMAL,
    val opacity: Float = 1f,
    val zIndex: Int,
    val updatedAt: Long,
    val textContent: TextLayerContent? = null,
    val solidColor: Int? = null
)

@Serializable
enum class LayerType {
    IMAGE,
    SOLID_COLOR,
    TEXT,
    AI_GENERATED;

    fun displayName(): String = when (this) {
        IMAGE -> "Image"
        SOLID_COLOR -> "Solid Color"
        TEXT -> "Text"
        AI_GENERATED -> "AI Generated"
    }
}
