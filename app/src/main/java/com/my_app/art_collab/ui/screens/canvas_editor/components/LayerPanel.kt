package com.my_app.art_collab.ui.screens.canvas_editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.my_app.art_collab.domain.model.Layer
import com.my_app.art_collab.domain.model.LayerType


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerPanel(
    layers: List<Layer>,
    selectedLayerId: String?,
    onDismiss: () -> Unit,
    onLayerClick: (Layer) -> Unit,
    onAddLayer: () -> Unit,
    onDeleteLayer: (Layer) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Layers", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onAddLayer) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add Layer")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            items(
                items = layers.sortedByDescending { it.zIndex },
                key = { it.id }
            ) { layer ->
                LayerItem(
                    layer = layer,
                    isSelected = layer.id == selectedLayerId,
                    onClick = { onLayerClick(layer) },
                    onDelete = { onDeleteLayer(layer) }
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerItem(
    layer: Layer,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            dismissValue == SwipeToDismissBoxValue.EndToStart
        }
    )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val isSwiping = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isSwiping) MaterialTheme.colorScheme.errorContainer else Color.Transparent),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isSwiping) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete Layer",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        color = if(layer.type == LayerType.SOLID_COLOR)
                            Color(layer.solidColor!!) else MaterialTheme.colorScheme.onSurfaceVariant

                    )
            ) {
                if(layer.type != LayerType.SOLID_COLOR) {
                    layer.sourceBitmapPath?.let { path ->
                        AsyncImage(
                            model = path,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = layer.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = layer.type.displayName(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
