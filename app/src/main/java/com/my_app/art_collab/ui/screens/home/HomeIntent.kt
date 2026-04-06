package com.my_app.art_collab.ui.screens.home

import com.my_app.art_collab.domain.model.Canvas

sealed class HomeIntent {
    data class SetSearchQuery(val query: String) : HomeIntent()
    data class SetSearchActive(val active: Boolean) : HomeIntent()
    data class DeleteCanvas(val canvas: Canvas) : HomeIntent()
    data class TogglePin(val canvasId: String, val isPinned: Boolean) : HomeIntent()
    data class RenameCanvas(val canvasId: String, val newName: String) : HomeIntent()
    data class CreateCanvas(val name: String, val widthPx: Int, val heightPx: Int) : HomeIntent()
    data class DuplicateCanvas(val canvas: Canvas) : HomeIntent()
    data class ShowContextMenu(val canvas: Canvas) : HomeIntent()
    data object DismissContextMenu : HomeIntent()
    data class ShowRenameDialog(val canvas: Canvas) : HomeIntent()
    data object DismissRenameDialog : HomeIntent()
    data class ShowDeleteDialog(val canvas: Canvas) : HomeIntent()
    data object DismissDeleteDialog : HomeIntent()
    data object ShowNewCanvasSheet : HomeIntent()
    data object DismissNewCanvasSheet : HomeIntent()
    data object ShowJoinCanvasDialog : HomeIntent()
    data object DismissJoinCanvasDialog : HomeIntent()
    data class JoinCanvas(val shareCode: String) : HomeIntent()
    data object ClearError : HomeIntent()
}

