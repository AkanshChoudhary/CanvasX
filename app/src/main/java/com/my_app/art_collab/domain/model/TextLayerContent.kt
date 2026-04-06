package com.my_app.art_collab.domain.model

import kotlinx.serialization.Serializable




@Serializable
data class TextLayerContent(
    val text: String,
    val fontFamily: String = "sans-serif",
    val fontSize: Float = 12f,
    val color: Int = 0xFF000000.toInt(),
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false)
