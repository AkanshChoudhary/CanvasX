package com.my_app.art_collab.ui.screens.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.my_app.art_collab.domain.model.Canvas
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CanvasCard(
    canvas: Canvas,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(canvas.widthPx.toFloat() / canvas.heightPx.toFloat())
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail or placeholder
            if (canvas.thumbnailLocalPath != null) {
                AsyncImage(
                    model = canvas.thumbnailLocalPath,
                    contentDescription = canvas.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            // Pin indicator
            if (canvas.isPinned) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = "Pinned",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Bottom scrim with name + timestamp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column {
                    Text(
                        text = canvas.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = canvas.updatedAt.toRelativeTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun Long.toRelativeTime(): String {
    val now = System.currentTimeMillis()
    val diff = now - this

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            "${minutes}m ago"
        }
        diff < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            "${hours}h ago"
        }
        diff < TimeUnit.DAYS.toMillis(2) -> "Yesterday"
        diff < TimeUnit.DAYS.toMillis(7) -> {
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            "${days}d ago"
        }
        else -> {
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            "${days / 7}w ago"
        }
    }
}

