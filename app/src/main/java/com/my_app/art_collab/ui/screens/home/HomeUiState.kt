package com.my_app.art_collab.ui.screens.home

import com.my_app.art_collab.domain.model.Canvas

data class HomeUiState(
    val canvases: List<Canvas> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val error: String? = null,
    val isNewCanvasSheetVisible: Boolean = false,
    val isJoinCanvasDialogVisible: Boolean = false,
    val isJoiningCanvas: Boolean = false,
    val contextMenuCanvas: Canvas? = null,
    val renameDialogCanvas: Canvas? = null,
    val deleteDialogCanvas: Canvas? = null,
    val newCanvasId: String? = null,
    val joinedCanvasId: String? = null,
    val isDeleteAccountDialogVisible: Boolean = false,
    val isDeletingAccount: Boolean = false
)

