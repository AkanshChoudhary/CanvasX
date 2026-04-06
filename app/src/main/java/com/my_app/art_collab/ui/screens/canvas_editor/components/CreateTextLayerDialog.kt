package com.my_app.art_collab.ui.screens.canvas_editor.components

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val textColors = listOf(
    Color.Black,
    Color.White,
    Color(0xFFF44336),
    Color(0xFFE91E63),
    Color(0xFF9C27B0),
    Color(0xFF3F51B5),
    Color(0xFF2196F3),
    Color(0xFF00BCD4),
    Color(0xFF009688),
    Color(0xFF4CAF50),
    Color(0xFFFFEB3B),
    Color(0xFFFF9800)
)

private val availableFonts = listOf(
    "Sans Serif" to "sans-serif",
    "Serif" to "serif",
    "Monospace" to "monospace",
    "Cursive" to "cursive",
    "Condensed" to "sans-serif-condensed",
    "Black" to "sans-serif-black",
    "Light" to "sans-serif-light",
    "Thin" to "sans-serif-thin"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTextLayerDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        text: String,
        fontFamily: String,
        color: Int,
        isBold: Boolean,
        isItalic: Boolean,
        isUnderline: Boolean
    ) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedColorArgb by remember { mutableIntStateOf(Color.Black.toArgb()) }
    var isBold by remember { mutableStateOf(false) }
    var isItalic by remember { mutableStateOf(false) }
    var isUnderline by remember { mutableStateOf(false) }
    var font by remember { mutableStateOf("sans-serif") }
    var selectedFontDisplayName by remember { mutableStateOf("Sans Serif") }
    var expanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Create Text Layer",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Enter text") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StyleToggleButton(
                        text = "B",
                        isActive = isBold,
                        onClick = { isBold = !isBold },
                        fontWeight = FontWeight.Bold
                    )
                    StyleToggleButton(
                        text = "I",
                        isActive = isItalic,
                        onClick = { isItalic = !isItalic },
                        fontStyle = FontStyle.Italic
                    )
                    StyleToggleButton(
                        text = "U",
                        isActive = isUnderline,
                        onClick = { isUnderline = !isUnderline },
                        textDecoration = TextDecoration.Underline
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Color", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    textColors.chunkedRow(6).first().forEach { color ->
                        ColorSwatch(
                            color = color,
                            isSelected = color.toArgb() == selectedColorArgb,
                            onClick = { selectedColorArgb = color.toArgb() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    textColors.chunkedRow(6).last().forEach { color ->
                        ColorSwatch(
                            color = color,
                            isSelected = color.toArgb() == selectedColorArgb,
                            onClick = { selectedColorArgb = color.toArgb() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedFontDisplayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Font") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        availableFonts.forEach { (displayName, familyName) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = displayName,
                                        fontFamily = FontFamily(
                                            Typeface.create(familyName, Typeface.NORMAL)
                                        )
                                    )
                                },
                                onClick = {
                                    font = familyName
                                    selectedFontDisplayName = displayName
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val typefaceStyle = when {
                    isBold && isItalic -> Typeface.BOLD_ITALIC
                    isBold -> Typeface.BOLD
                    isItalic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text.ifEmpty { "Preview" },
                        fontFamily = FontFamily(Typeface.create(font, typefaceStyle)),
                        textDecoration = if (isUnderline) TextDecoration.Underline else TextDecoration.None,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = if (text.isEmpty())
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else Color(selectedColorArgb)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            if (text.isNotBlank()) {
                                onConfirm(text, font, selectedColorArgb, isBold, isItalic, isUnderline)
                            }
                        },
                        enabled = text.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }
            }

        }
    }
}

private fun <T> List<T>.chunkedRow(size: Int): List<List<T>> = chunked(size)

@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color, CircleShape)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = if (color == Color.Black || color == Color(0xFF3F51B5) ||
                    color == Color(0xFF9C27B0) || color == Color(0xFF009688)
                ) Color.White else Color.Black,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun StyleToggleButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    fontWeight: FontWeight = FontWeight.Normal,
    fontStyle: FontStyle = FontStyle.Normal,
    textDecoration: TextDecoration = TextDecoration.None
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    textDecoration = textDecoration
                ),
                color = if (isActive)
                    MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
