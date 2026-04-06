package com.my_app.art_collab.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "canvases")
data class CanvasEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    @ColumnInfo(name = "collaborator_ids_json")
    val collaboratorIdsJson: String,
    val shareCode: String = "",
    val isViewOnly: Boolean,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailLocalPath: String?
)

