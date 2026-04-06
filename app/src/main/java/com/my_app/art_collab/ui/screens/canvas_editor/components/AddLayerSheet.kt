package com.my_app.art_collab.ui.screens.canvas_editor.components

import android.net.Uri
import com.my_app.art_collab.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLayerSheet(
    onDismiss: () -> Unit,
    onImageSelected: (Uri) -> Unit,
    onSolidColorClick: () -> Unit,
    onAiGenerateClick: () -> Unit
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImageSelected(uri)
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Add Layer",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )

            ListItem(
                headlineContent = { Text("Pick Image") },
                supportingContent = { Text("Import an image from your device") },
                leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Add Image") },
                modifier = Modifier.clickable {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(
                            mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }
            )
            ListItem(
                headlineContent = { Text("Pick Solid Color") },
                supportingContent = { Text("Add a flat fill layer") },
                leadingContent = { Icon(Icons.Default.Palette, contentDescription = "Pick Solid Color") },
                modifier = Modifier.clickable {
                    onSolidColorClick()
                }
            )
            ListItem(
                headlineContent = { Text("Generate with AI") },
                supportingContent = { Text("Ask Gemini to create an image") },
                leadingContent = { Icon(Icons.Default.AutoAwesome, contentDescription = "Generate with AI") },
                modifier = Modifier.clickable {
                    onAiGenerateClick()
                }
            )
        }
    }
}
