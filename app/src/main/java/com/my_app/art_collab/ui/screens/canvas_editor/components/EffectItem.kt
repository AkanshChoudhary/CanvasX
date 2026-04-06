package com.my_app.art_collab.ui.screens.canvas_editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.my_app.art_collab.domain.model.Effect


@Composable
fun EffectItem(
    effect: Effect,
    onUpdate: (Effect) -> Unit,
    onRemove: ()-> Unit,
    onToggleEnabled: ()-> Unit
){
    var isExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth()
    ){
        Row(
            modifier = Modifier.fillMaxWidth().clickable{
                isExpanded = !isExpanded
            }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = effect.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                color = if (effect.isEnabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
            )
            // Enabled toggle
            Switch(
                checked = effect.isEnabled,
                onCheckedChange = { onToggleEnabled() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Delete button
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove effect",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            // Expand/collapse
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand"
            )
        }
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (effect) {
                    is Effect.BrightnessContrast -> BrightnessContrastSliders(effect, onUpdate)
                    is Effect.GaussianBlur -> BlurSliders(effect, onUpdate)
                    is Effect.Saturation -> SaturationSliders(effect, onUpdate)
                    is Effect.Vignette -> VignetteSliders(effect, onUpdate)
                    is Effect.Sharpen -> SharpenSliders(effect, onUpdate)
                    is Effect.Exposure -> ExposureSliders(effect, onUpdate)
                    is Effect.ColorTemperature -> TemperatureSliders(effect, onUpdate)
                    is Effect.Grain -> GrainSliders(effect, onUpdate)
                    is Effect.Pixelate -> PixelateSliders(effect, onUpdate)
                }
            }
        }
        HorizontalDivider()
    }
}
@Composable
private fun BrightnessContrastSliders(
    effect: Effect.BrightnessContrast,
    onUpdate: (Effect) -> Unit
) {
    var brightness by remember(effect.id) {
        mutableFloatStateOf(effect.brightness) }
    var contrast by remember(effect.id) {
        mutableFloatStateOf(effect.contrast) }
    LabeledSlider(
        label = "Brightness",
        value = brightness,
        valueRange = -1f..1f,
        onValueChange = { brightness = it },
        onValueChangeFinished = {
            onUpdate(effect.copy(brightness = brightness))
        }
    )
    LabeledSlider(
        label = "Contrast",
        value = contrast,
        valueRange = -1f..1f,
        onValueChange = { contrast = it },
        onValueChangeFinished = {
            onUpdate(effect.copy(contrast = contrast))
        }
    )
}
@Composable
private fun BlurSliders(
    effect: Effect.GaussianBlur,
    onUpdate: (Effect) -> Unit
) {
    var radius by remember(effect.id) { mutableFloatStateOf(effect.radius)
    }
    LabeledSlider(
        label = "Radius",
        value = radius,
        valueRange = 0f..50f,
        onValueChange = { radius = it },
        onValueChangeFinished = {
            onUpdate(effect.copy(radius = radius))
        }
    )
}
@Composable
private fun SaturationSliders(
    effect: Effect.Saturation,
    onUpdate: (Effect) -> Unit
) {
    var amount by remember(effect.id) { mutableFloatStateOf(effect.amount)
    }
    LabeledSlider(
        label = "Saturation",
        value = amount,
        valueRange = -1f..1f,
        onValueChange = { amount = it },
        onValueChangeFinished = {
            onUpdate(effect.copy(amount = amount))
        }
    )
}
@Composable
private fun VignetteSliders(
    effect: Effect.Vignette,
    onUpdate: (Effect) -> Unit
) {
    var intensity by remember(effect.id) {
        mutableFloatStateOf(effect.intensity) }
    var feather by remember(effect.id) {
        mutableFloatStateOf(effect.feather) }
    LabeledSlider(
        label = "Intensity",
        value = intensity,
        valueRange = 0f..1f,
        onValueChange = { intensity = it },
        onValueChangeFinished = {
            onUpdate(effect.copy(intensity = intensity))
        }
    )
    LabeledSlider(
        label = "Feather",
        value = feather,
        valueRange = 0f..1f,
        onValueChange = { feather = it },
        onValueChangeFinished = {
            onUpdate(effect.copy(feather = feather))
        }
    )
}
@Composable
private fun SharpenSliders(
    effect: Effect.Sharpen,
    onUpdate: (Effect) -> Unit
) {
    var amount by remember(effect.id) { mutableFloatStateOf(effect.amount)
    }
    LabeledSlider(
        label = "Amount",
        value = amount,
        valueRange = 0f..1f,
        onValueChange = { amount = it },
        onValueChangeFinished = {
            onUpdate(effect.copy(amount = amount))
        }
    )
}
@Composable
private fun ExposureSliders(
    effect: Effect.Exposure,
    onUpdate: (Effect) -> Unit
) {
    var stops by remember(effect.id) { mutableFloatStateOf(effect.stops) }
    LabeledSlider(
        label = "Exposure (EV)",
        value = stops,
        valueRange = -3f..3f,
        onValueChange = { stops = it },
        onValueChangeFinished = {
            onUpdate(effect.copy(stops = stops))
        }
    )
}
@Composable
private fun TemperatureSliders(
    effect: Effect.ColorTemperature,
    onUpdate: (Effect) -> Unit
) {
    var temperature by remember(effect.id) {
        mutableFloatStateOf(effect.temperature) }
    var tint by remember(effect.id) { mutableFloatStateOf(effect.tint) }
    LabeledSlider(
        label = "Temperature",
        value = temperature,
        valueRange = -1f..1f,
        onValueChange = { temperature = it },
        onValueChangeFinished = {
            onUpdate(effect.copy(temperature = temperature))
        }
    )
    LabeledSlider(
        label = "Tint",
        value = tint,
        valueRange = -1f..1f,
        onValueChange = { tint = it },
        onValueChangeFinished = {
            onUpdate(effect.copy(tint = tint))
        }
    )
}
@Composable
private fun GrainSliders(
    effect: Effect.Grain,
    onUpdate: (Effect) -> Unit
) {
    var amount by remember(effect.id) { mutableFloatStateOf(effect.amount)
    }
    var size by remember(effect.id) { mutableFloatStateOf(effect.size) }
    LabeledSlider(
        label = "Amount",
        value = amount,
        valueRange = 0f..1f,
        onValueChange = { amount = it },
        onValueChangeFinished = {
            onUpdate(effect.copy(amount = amount))
        }
    )
    LabeledSlider(
        label = "Size",
        value = size,
        valueRange = 0.5f..3f,
        onValueChange = { size = it },
        onValueChangeFinished = {
            onUpdate(effect.copy(size = size))
        }
    )
}
@Composable
private fun PixelateSliders(
    effect: Effect.Pixelate,
    onUpdate: (Effect) -> Unit
) {
    var blockSize by remember(effect.id) { mutableFloatStateOf(effect.blockSize) }
    LabeledSlider(
        label = "Block Size",
        value = blockSize,
        valueRange = 2f..100f,
        onValueChange = { blockSize = it },
        onValueChangeFinished = {
            onUpdate(effect.copy(blockSize = blockSize))
        }
    )
}
@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "%.2f".format(value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.fillMaxWidth()
        )
    }
}