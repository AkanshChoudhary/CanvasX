package com.my_app.art_collab.data.local.db

import com.my_app.art_collab.data.local.db.entity.CanvasEntity
import com.my_app.art_collab.domain.model.Canvas
import com.my_app.art_collab.domain.model.CanvasMember
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

// ── Canvas Mappers ───────────────────────────────────────────────────────────

fun CanvasEntity.toDomain(): Canvas = Canvas(
    id = id,
    ownerId = ownerId,
    name = name,
    widthPx = widthPx,
    heightPx = heightPx,
    members = try {
        json.decodeFromString<Map<String, CanvasMember>>(membersJson)
    } catch (_: Exception) {
        emptyMap()
    },
    shareCode = shareCode,
    isViewOnly = isViewOnly,
    createdAt = createdAt,
    updatedAt = updatedAt,
    thumbnailLocalPath = thumbnailLocalPath,
    isPinned = isPinned
)

fun Canvas.toEntity(): CanvasEntity = CanvasEntity(
    id = id,
    ownerId = ownerId,
    name = name,
    widthPx = widthPx,
    heightPx = heightPx,
    membersJson = json.encodeToString(members),
    shareCode = shareCode,
    isViewOnly = isViewOnly,
    createdAt = createdAt,
    updatedAt = updatedAt,
    thumbnailLocalPath = thumbnailLocalPath,
    isPinned = isPinned
)
