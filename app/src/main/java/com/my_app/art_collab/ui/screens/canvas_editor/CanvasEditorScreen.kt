package com.my_app.art_collab.ui.screens.canvas_editor

import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LensBlur
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.my_app.art_collab.ui.screens.canvas_editor.components.AddLayerSheet
import com.my_app.art_collab.ui.screens.canvas_editor.components.AiGenerationDialog
import com.my_app.art_collab.ui.screens.canvas_editor.components.BlendModePickerSheet
import com.my_app.art_collab.ui.screens.canvas_editor.components.ColorPickerDialog
import com.my_app.art_collab.ui.screens.canvas_editor.components.CreateTextLayerDialog
import com.my_app.art_collab.ui.screens.canvas_editor.components.EffectChainSheet
import com.my_app.art_collab.ui.screens.canvas_editor.components.LayerPanel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasEditorScreen(
    canvasId: String,
    name: String,
    ownerId: String,
    widthPx: Int,
    heightPx: Int,
    onNavigateBack: () -> Unit
) {
    val viewModel: CanvasEditorViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val localLifecycleOwner = LocalLifecycleOwner.current
    val layers by viewModel.layers.collectAsState()
    val selectedLayerId by viewModel.selectedLayerId.collectAsState()
    val compositedBitmap by viewModel.compositedBitmap.collectAsState()
    val isRendering by viewModel.isRendering.collectAsState()
    val aiState by viewModel.aiState.collectAsState()
    val canvasInteractionBlocked by viewModel.canvasInteractionBlocked.collectAsState()
    val preloadedRemoteBitmaps by viewModel.preloadedRemoteBitmaps.collectAsState()
    val kickedFromCanvasOverlay by viewModel.kickedFromCanvasOverlay.collectAsState()

    var showLayersPanel by remember { mutableStateOf(false) }
    var showAddLayerSheet by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showEffectsSheet by remember { mutableStateOf(false) }
    var showBlendSheet by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.exportMessages.collectLatest { message->
            snackbarHostState.showSnackbar(message)
        }
    }
    val context = LocalContext.current
    LaunchedEffect(viewModel, context) {
        viewModel.shareImageRequests.collectLatest { req ->
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, req.uri)
                putExtra(Intent.EXTRA_SUBJECT, req.displayName)
                putExtra(Intent.EXTRA_TEXT, req.displayName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, req.displayName, req.uri)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share image"))
        }
    }

    LaunchedEffect(canvasId, viewModel) {
        viewModel.exitCanvasAfterKick.collect {
            viewModel.awaitPersistThumbnailForLeave(canvasId)
            onNavigateBack()
        }
    }
    // Size must be set before hydration so the render engine can composite at the correct resolution.
    LaunchedEffect(widthPx, heightPx) {
        viewModel.setCanvasSize(widthPx, heightPx)
    }

    LaunchedEffect(canvasId, widthPx, heightPx) {
        if (widthPx > 0 && heightPx > 0) {
            viewModel.loadCanvasLayers(canvasId, ownerId)
        }
    }

    LaunchedEffect(canvasId) {
        viewModel.startIdleThumbnailCollector(canvasId)
    }

    DisposableEffect(localLifecycleOwner, canvasId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onLocalLifecycleStopThumbnail(canvasId)
            }
        }
        localLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { localLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler {
        scope.launch {
            viewModel.awaitPersistThumbnailForLeave(canvasId)
            onNavigateBack()
        }
    }

    DisposableEffect(canvasId) {
        val owner = ProcessLifecycleOwner.get()
        var seenProcessStop = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    seenProcessStop = true
                }
                Lifecycle.Event.ON_START -> {
                    if (seenProcessStop) {
                        viewModel.onCanvasForeground(canvasId, ownerId)
                        viewModel.refreshHydrationAfterBackground(canvasId)
                    }
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // Auto-select first layer when layers change and nothing is selected
    LaunchedEffect(layers, selectedLayerId) {
        if (selectedLayerId == null && layers.isNotEmpty()) {
            viewModel.selectLayer(layers.first().id)
        }
    }

    val isDark = isSystemInDarkTheme()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = name,
                        color = if (isDark)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark)
                        MaterialTheme.colorScheme.surface
                    else
                        MaterialTheme.colorScheme.primaryContainer,
                    navigationIconContentColor = if (isDark)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                viewModel.awaitPersistThumbnailForLeave(canvasId)
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            EditorToolbar(
                showLayersPanel = showLayersPanel,
                showAddLayerSheet = showAddLayerSheet,
                showEffectsSheet = showEffectsSheet,
                hasSelectedLayer = selectedLayerId != null,
                onLayersClick = { showLayersPanel = true },
                onAddClick = { showAddLayerSheet = true },
                onEffectsClick = { showEffectsSheet = true },
                onBlendClick = { showBlendSheet = true },
                onAddTextLayerClick = {showTextDialog = true},
                onShareClick = { viewModel.shareComposite(name) }
            )
        }
    ) { paddingValues ->
        CanvasViewport(
            canvasWidthPx = widthPx,
            canvasHeightPx = heightPx,
            compositedBitmap = compositedBitmap,
            isRendering = isRendering,
            layers = layers,
            selectedLayerId = selectedLayerId,
            preloadedRemoteBitmaps = preloadedRemoteBitmaps,
            getProcessedBitmap = viewModel::getProcessedBitmap,
            onUpdateTransform = { layerId, transform ->
                viewModel.updateLayerTransform(canvasId,layerId, transform)
            },

            onDragEnd = { layerId ->
                viewModel.finalizeTransform(canvasId,layerId)
            },
            modifier = Modifier.padding(paddingValues)
        )
    }

    // Dialogs and Sheets
    if (showLayersPanel) {
        LayerPanel(
            layers = layers,
            selectedLayerId = selectedLayerId,
            onDismiss = { showLayersPanel = false },
            onLayerClick = { layer -> viewModel.selectLayer(layer.id) },
            onAddLayer = {
                showLayersPanel = false
                showAddLayerSheet = true
            },
            onDeleteLayer = { layer ->
                viewModel.deleteLayer(canvasId = canvasId,layer.id)
            }
        )
    }

    if (showAddLayerSheet) {
        AddLayerSheet(
            onDismiss = { showAddLayerSheet = false },
            onImageSelected = { uri ->
                // Get image dimensions without loading full bitmap
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream, null, options)
                    }
                    val imageWidth = options.outWidth
                    val imageHeight = options.outHeight
                    viewModel.addImageLayer(canvasId, uri.toString(), imageWidth, imageHeight)
                } catch (e: Exception) {
                    // Fallback if we can't read dimensions
                    viewModel.addImageLayer(canvasId, uri.toString(), 0, 0)
                }
                showAddLayerSheet = false
            },
            onSolidColorClick = {
                showAddLayerSheet = false
                showColorPicker = true
            },
            onAiGenerateClick = {
                showAddLayerSheet = false
                showAiDialog = true
            }
        )
    }

    if (showColorPicker) {
        ColorPickerDialog(
            onDismiss = { showColorPicker = false },
            onColorSelected = { colorArgb ->
                viewModel.addSolidColorLayer(canvasId, colorArgb)
                showColorPicker = false
            }
        )
    }


    if (showTextDialog) {
        CreateTextLayerDialog(
            onDismiss = { showTextDialog = false },
            onConfirm = { text, fontFamily, color, isBold, isItalic, isUnderline ->
                viewModel.addNewTextLayer(
                    canvasId, text, fontFamily, color, isBold, isItalic, isUnderline
                )
                showTextDialog = false
            }
        )
    }

    if (showAiDialog) {
        AiGenerationDialog(
            aiState = aiState,
            onGenerate = { prompt ->
                viewModel.generateAiLayer(canvasId, prompt)
            },
            onDismiss = {
                showAiDialog = false
                viewModel.clearAiError()
            }
        )
    }

    LaunchedEffect(aiState) {
        if (aiState is AiGenerationState.Success) {
            showAiDialog = false
            viewModel.clearAiError()
        }
    }

    // Effects Sheet
    if (showEffectsSheet && selectedLayerId != null) {
        val selectedLayer = layers.find { it.id == selectedLayerId }
        if (selectedLayer != null) {
            EffectChainSheet(
                layer = selectedLayer,
                effectChain = selectedLayer.effectChain,
                onAddEffect = { effect ->
                    viewModel.addEffect(canvasId, selectedLayerId!!, effect)
                },
                onUpdateEffect = { updated ->
                    viewModel.updateEffect(canvasId, selectedLayerId!!, updated)
                },
                onRemoveEffect = { effectId ->
                    viewModel.removeEffect(canvasId, selectedLayerId!!, effectId)
                },
                onToggleEffect = { effectId ->
                    viewModel.toggleEffect(canvasId, selectedLayerId!!, effectId)
                },
                onDismiss = { showEffectsSheet = false }
            )
        }
    }

    if (canvasInteractionBlocked) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = "Loading canvas…",
                        modifier = Modifier.padding(top = 16.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    if (kickedFromCanvasOverlay) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = "You were removed from this canvas",
                        modifier = Modifier.padding(top = 16.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    if(showBlendSheet && selectedLayerId!=null){
        val selectedLayer = layers.find { it.id == selectedLayerId }
        BlendModePickerSheet(
            layer = selectedLayer!!,
            currentBlendMode = selectedLayer.blendMode,
            currentOpacity = selectedLayer.opacity,
            onBlendModeSelected = { mode ->
                viewModel.setBlendMode(canvasId,selectedLayerId!!, mode)
            },
            onOpacityChanged = { opacity ->
                viewModel.setLayerOpacity(canvasId,selectedLayerId!!, opacity)
            },
            onDismiss = {showBlendSheet = false}
        )
    }
}

@Composable
private fun EditorToolbar(
    showLayersPanel: Boolean,
    showAddLayerSheet: Boolean,
    showEffectsSheet: Boolean,
    hasSelectedLayer: Boolean,
    onLayersClick: () -> Unit,
    onAddClick: () -> Unit,
    onEffectsClick: () -> Unit,
    onBlendClick: () -> Unit,
    onAddTextLayerClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 0.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolbarButton(
                icon = Icons.Filled.Layers,
                label = "Layers",
                isActive = showLayersPanel,
                onClick = onLayersClick
            )
            ToolbarButton(
                icon = Icons.Filled.Star,
                label = "Effects",
                isActive = showEffectsSheet,
                enabled = hasSelectedLayer,
                onClick = onEffectsClick
            )
            ToolbarButton(
                icon = Icons.Filled.LensBlur,
                label = "Blend",
                enabled = hasSelectedLayer,
                onClick = onBlendClick
            )
            ToolbarButton(
                icon = Icons.Filled.TextFields,
                label = "Text",
                enabled = true,
                onClick = onAddTextLayerClick
            )
            ToolbarButton(
                icon = Icons.Filled.Add,
                label = "Add",
                isActive = showAddLayerSheet,
                onClick = onAddClick
            )
            ToolbarButton(
                icon = Icons.Filled.Share,
                label = "Share",
                onClick = onShareClick
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isActive: Boolean = false,
    enabled: Boolean = true
) {
    val activeBackground = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
    val iconTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        isActive  -> MaterialTheme.colorScheme.primary
        else      -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val labelColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        isActive  -> MaterialTheme.colorScheme.primary
        else      -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) activeBackground else Color.Transparent)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}
