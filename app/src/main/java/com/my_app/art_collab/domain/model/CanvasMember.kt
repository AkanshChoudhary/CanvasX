package com.my_app.art_collab.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CanvasMember(
    val role: String,
    val joinedAt: Long = 0L
) {
    companion object {
        const val ROLE_OWNER = "owner"
        const val ROLE_EDITOR = "editor"
    }
}
