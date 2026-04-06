package com.my_app.art_collab.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CollaboratorRole {
    OWNER,
    EDITOR,
    VIEWER
}

@Serializable
data class Collaborator(
    val userId: String,
    val displayName: String,
    val photoUrl: String? = null,
    val role: CollaboratorRole,
    val joinedAt: Long
)
