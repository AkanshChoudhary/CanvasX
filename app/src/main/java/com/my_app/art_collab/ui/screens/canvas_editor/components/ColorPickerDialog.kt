package com.my_app.art_collab.ui.screens.canvas_editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    initialColor: Int = android.graphics.Color.BLUE,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var color by remember { mutableStateOf(Color(initialColor)) }
    
    var red by remember { mutableStateOf(color.red) }
    var green by remember { mutableStateOf(color.green) }
    var blue by remember { mutableStateOf(color.blue) }
    var alpha by remember { mutableStateOf(color.alpha) }

    val presetColors = listOf(
        Color.Red, Color.Green, Color.Blue, Color.Yellow,
        Color.Cyan, Color.Magenta, Color(0xFFFFA500), // Orange
        Color(0xFF800080), // Purple
        Color(0xFF8B4513), // Brown
        Color.Black, Color.White, Color.Gray
    )

    LaunchedEffect(red, green, blue, alpha) {
        color = Color(red, green, blue, alpha)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick Solid Color") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Color Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                )

                // Color Swatches
                Text("Presets", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColors.forEach { presetColor ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(presetColor)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable {
                                    red = presetColor.red
                                    green = presetColor.green
                                    blue = presetColor.blue
                                    alpha = presetColor.alpha
                                }
                        )
                    }
                }

                // Sliders Section
                Text("Custom Color", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ColorSlider(label = "Red", value = red, onValueChange = { red = it }, color = Color.Red)
                    ColorSlider(label = "Green", value = green, onValueChange = { green = it }, color = Color.Green)
                    ColorSlider(label = "Blue", value = blue, onValueChange = { blue = it }, color = Color.Blue)
                    ColorSlider(label = "Alpha", value = alpha, onValueChange = { alpha = it }, color = Color.Gray)
                }
                
                var hexText by remember(color) { 
                    mutableStateOf(String.format("%08X", color.toArgb())) 
                }
                
                OutlinedTextField(
                    value = hexText,
                    onValueChange = {
                        hexText = it
                        try {
                            if (it.length == 8) {
                                val parsed = android.graphics.Color.parseColor("#$it")
                                val newColor = Color(parsed)
                                red = newColor.red
                                green = newColor.green
                                blue = newColor.blue
                                alpha = newColor.alpha
                            }
                        } catch (e: Exception) {}
                    },
                    label = { Text("Hex Code (AARRGGBB)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(color.toArgb()) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text((value * 255).toInt().toString(), style = MaterialTheme.typography.labelSmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color
            )
        )
    }
}
