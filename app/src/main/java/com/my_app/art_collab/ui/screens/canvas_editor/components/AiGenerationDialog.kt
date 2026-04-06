package com.my_app.art_collab.ui.screens.canvas_editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.my_app.art_collab.ui.screens.canvas_editor.AiGenerationState

@Composable
fun AiGenerationDialog(
    aiState: AiGenerationState,
    onGenerate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    val isLoading = aiState is AiGenerationState.Loading

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Generate with AI",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Describe the image...") },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                if (aiState is AiGenerationState.Error) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = aiState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { onGenerate(prompt) },
                        enabled = prompt.isNotBlank() && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generating...")
                        } else {
                            Text("Generate")
                        }
                    }
                }
            }
        }
    }
}
