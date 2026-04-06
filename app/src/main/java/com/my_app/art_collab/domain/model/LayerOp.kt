package com.my_app.art_collab.domain.model

import java.util.UUID

data class LayerOp(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val layerId: String = "",
    val type: String = "",
    val payload: Map<String,Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

