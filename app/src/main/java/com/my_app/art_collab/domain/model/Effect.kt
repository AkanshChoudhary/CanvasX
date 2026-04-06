package com.my_app.art_collab.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
sealed class Effect {
    abstract val id: String
    abstract val isEnabled: Boolean
    abstract val name: String

    @Serializable
    data class BrightnessContrast(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val brightness: Float = 0f,
        val contrast: Float = 0f
    ) : Effect() {
        override val name: String = "Brightness & Contrast"
    }

    @Serializable
    data class Exposure(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val stops: Float = 0f
    ) : Effect() {
        override val name: String = "Exposure"
    }

    @Serializable
    data class GaussianBlur(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val radius: Float = 5f
    ) : Effect() {
        override val name: String = "Blur"
    }

    @Serializable
    data class Sharpen(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val amount: Float = 0.5f
    ) : Effect() {
        override val name: String = "Sharpen"
    }

    @Serializable
    data class Vignette(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val intensity: Float = 0.5f,
        val feather: Float = 0.5f
    ) : Effect() {
        override val name: String = "Vignette"
    }

    @Serializable
    data class Saturation(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val amount: Float = 0f
    ) : Effect() {
        override val name: String = "Saturation"
    }

    @Serializable
    data class ColorTemperature(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val temperature: Float = 0f,
        val tint: Float = 0f
    ) : Effect() {
        override val name: String = "Temperature & Tint"
    }

    @Serializable
    data class Grain(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val amount: Float = 0.3f,
        val size: Float = 1f
    ) : Effect() {
        override val name: String = "Film Grain"
    }

    @Serializable
    data class Pixelate(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val blockSize: Float = 2f
    ) : Effect() {
        override val name: String = "Pixelate"
    }
}
