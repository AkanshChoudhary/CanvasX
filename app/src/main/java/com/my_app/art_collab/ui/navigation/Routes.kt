package com.my_app.art_collab.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Screen {
    @Serializable
    data object Auth : Screen

    @Serializable
    data object Home : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data class NewCanvas(
        val canvasId: String,
        val name: String,
        val widthPx: Int,   // Canvas logical pixel width  — e.g. 1080
        val heightPx: Int   // Canvas logical pixel height — e.g. 1920
    ) : Screen
}
