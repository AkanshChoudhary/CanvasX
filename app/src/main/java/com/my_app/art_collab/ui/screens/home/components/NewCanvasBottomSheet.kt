package com.my_app.art_collab.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

data class SizePreset(
    val label: String,
    val width: Int,
    val height: Int
)

private val presets = listOf(
    SizePreset("Square", 1080, 1080),
    SizePreset("Portrait", 1080, 1350),
    SizePreset("Landscape", 1920, 1080),
    SizePreset("Wallpaper", 1080, 2340),
    SizePreset("Poster", 1080, 1527),
    SizePreset("Custom", 0, 0)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCanvasBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onCreate: (name: String, widthPx: Int, heightPx: Int) -> Unit
) {
    var selectedPreset by remember { mutableStateOf(presets[0]) }
    var canvasName by remember { mutableStateOf("") }
    var customWidth by remember { mutableStateOf("") }
    var customHeight by remember { mutableStateOf("") }

    val isCustom = selectedPreset.label == "Custom"
    val finalWidth = if (isCustom) customWidth.toIntOrNull() ?: 0 else selectedPreset.width
    val finalHeight = if (isCustom) customHeight.toIntOrNull() ?: 0 else selectedPreset.height
    val isCreateEnabled = finalWidth > 0 && finalHeight > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "New Canvas",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Size presets
            Text(
                text = "Size",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(presets) { preset ->
                    FilterChip(
                        selected = selectedPreset == preset,
                        onClick = { selectedPreset = preset },
                        label = {
                            Text(
                                if (preset.label == "Custom") preset.label
                                else "${preset.label} (${preset.width}×${preset.height})"
                            )
                        }
                    )
                }
            }

            // Custom size inputs
            if (isCustom) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = customWidth,
                        onValueChange = { customWidth = it.filter { c -> c.isDigit() } },
                        label = { Text("Width") },
                        suffix = { Text("px") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customHeight,
                        onValueChange = { customHeight = it.filter { c -> c.isDigit() } },
                        label = { Text("Height") },
                        suffix = { Text("px") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Canvas name
            OutlinedTextField(
                value = canvasName,
                onValueChange = { canvasName = it },
                label = { Text("Canvas name") },
                placeholder = { Text("Untitled Canvas") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Create button
            Button(
                onClick = {
                    onCreate(
                        canvasName.ifBlank { "Untitled Canvas" },
                        finalWidth,
                        finalHeight
                    )
                },
                enabled = isCreateEnabled,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Create Canvas")
            }
        }
    }
}


