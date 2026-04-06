package com.my_app.art_collab.ui.screens.canvas_editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.my_app.art_collab.domain.model.BlendMode
import com.my_app.art_collab.domain.model.Layer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlendModePickerSheet(
    layer: Layer,
    currentBlendMode: BlendMode,
    currentOpacity: Float,
    onBlendModeSelected: (BlendMode) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Blend - ${layer.name}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Opacity", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "${(currentOpacity * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Slider(
                value = currentOpacity,
                onValueChange = onOpacityChanged,
                valueRange = 0f..1f,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Text(
                text = "Blend Mode",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            BlendMode.entries.forEach { mode ->
                BlendModeItem(
                    blendMode = mode,
                    isSelected = mode == currentBlendMode,
                    onClick = { onBlendModeSelected(mode) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BlendModeItem(
    blendMode: BlendMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = blendMode.displayName(),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = blendMode.description(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun BlendMode.displayName(): String = when (this) {
    BlendMode.NORMAL -> "Normal"
    BlendMode.MULTIPLY -> "Multiply"
    BlendMode.SCREEN -> "Screen"
    BlendMode.OVERLAY -> "Overlay"
    BlendMode.SOFT_LIGHT -> "Soft Light"
}

private fun BlendMode.description(): String = when (this) {
    BlendMode.NORMAL -> "Standard blending"
    BlendMode.MULTIPLY -> "Darkens — good for shadows"
    BlendMode.SCREEN -> "Lightens — good for glows"
    BlendMode.OVERLAY -> "Boosts contrast"
    BlendMode.SOFT_LIGHT -> "Subtle contrast & saturation"
}
