package com.my_app.art_collab.ui.screens.canvas_editor.components
import android.app.AlertDialog
import android.graphics.drawable.Icon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Vignette
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.my_app.art_collab.domain.model.Effect


data class EffectOption(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val createEffect: () -> Effect
)
private val availableEffects = listOf(
    EffectOption(
        name = "Brightness & Contrast",
        description = "Adjust light and dark tones",
        icon = Icons.Default.Brightness6,
        createEffect = { Effect.BrightnessContrast() }
    ),
    EffectOption(
        name = "Blur",
        description = "Soften the image",
        icon = Icons.Default.BlurOn,
        createEffect = { Effect.GaussianBlur() }
    ),
    EffectOption(
        name = "Saturation",
        description = "Adjust color intensity",
        icon = Icons.Default.Contrast,
        createEffect = { Effect.Saturation() }
    ),
    EffectOption(
        name = "Exposure",
        description = "Adjust overall brightness",
        icon = Icons.Default.Adjust,
        createEffect = { Effect.Exposure() }
    ),
    EffectOption(
        name = "Sharpen",
        description = "Enhance edge detail",
        icon = Icons.Default.FilterVintage,
        createEffect = { Effect.Sharpen() }
    ),
    EffectOption(
        name = "Vignette",
        description = "Darken the edges",
        icon = Icons.Default.Vignette,
        createEffect = { Effect.Vignette() }
    ),
    EffectOption(
        name = "Temperature & Tint",
        description = "Adjust color warmth",
        icon = Icons.Default.Thermostat,
        createEffect = { Effect.ColorTemperature() }
    ),
    EffectOption(
        name = "Film Grain",
        description = "Add nostalgic texture",
        icon = Icons.Default.Grain,
        createEffect = { Effect.Grain() }
    ),
    EffectOption(
        name = "Pixelate",
        description = "Retro pixel effect",
        icon = Icons.Default.GridOn,
        createEffect = { Effect.Pixelate() }
    )
)

@Composable
fun EffectPickerDialog(
    onDismiss: () -> Unit,
    onEffectSelected: (Effect) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Effect")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                availableEffects.forEach { option ->
                    ListItem(
                        headlineContent = {
                            Text(option.name, style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = option.name,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                onEffectSelected(option.createEffect())
                            }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}