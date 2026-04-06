package com.my_app.art_collab.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class BlendMode {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    SOFT_LIGHT
}