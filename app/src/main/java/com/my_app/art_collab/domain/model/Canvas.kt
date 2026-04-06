package com.my_app.art_collab.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Canvas(
    val id: String,
    val ownerId: String,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val layers: List<Layer> = emptyList(),
    val collaboratorIds: List<String> = emptyList(),
    val shareCode: String = "",
    val isViewOnly: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailLocalPath: String? = null,
    val isPinned: Boolean = false
)
