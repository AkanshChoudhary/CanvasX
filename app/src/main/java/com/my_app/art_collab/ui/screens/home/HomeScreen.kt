package com.my_app.art_collab.ui.screens.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.my_app.art_collab.domain.model.Canvas
import com.my_app.art_collab.ui.screens.home.components.CanvasCard
import com.my_app.art_collab.ui.screens.home.components.NewCanvasBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenNewCanvas: (String, String, Int, Int) -> Unit = { _, _, _, _ -> },  // id, name, widthPx, heightPx
    onLoggedOut: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredCanvases by viewModel.filteredCanvases.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    // Error handling
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.handleIntent(HomeIntent.ClearError)
        }
    }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Navigate once when join succeeds; consume id so returning from editor does not re-trigger.
    LaunchedEffect(uiState.joinedCanvasId, uiState.canvases) {
        val canvasId = uiState.joinedCanvasId ?: return@LaunchedEffect
        val canvas = uiState.canvases.find { it.id == canvasId } ?: return@LaunchedEffect
        onOpenNewCanvas(canvas.id, canvas.name, canvas.widthPx, canvas.heightPx)
        viewModel.handleIntent(HomeIntent.ConsumeJoinedCanvasId)
    }

    LaunchedEffect(Unit) {
        viewModel.logoutCompleted.collect {
            onLoggedOut()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isSearchActive) {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.handleIntent(HomeIntent.SetSearchQuery(it)) },
                    onClose = { viewModel.handleIntent(HomeIntent.SetSearchActive(false)) },
                    overflowMenuExpanded = overflowMenuExpanded,
                    onOverflowMenuExpandedChange = { overflowMenuExpanded = it },
                    onAboutClick = { showAbout = true },
                    onLogoutClick = { showLogoutConfirm = true }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = "CanvasX",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    },
                    actions = {
                        HomeOverflowMenu(
                            expanded = overflowMenuExpanded,
                            onExpandedChange = { overflowMenuExpanded = it },
                            onAboutClick = { showAbout = true },
                            onLogoutClick = { showLogoutConfirm = true }
                        )
                        IconButton(
                            onClick = { viewModel.handleIntent(HomeIntent.SetSearchActive(true)) }
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { viewModel.handleIntent(HomeIntent.ShowJoinCanvasDialog) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Filled.GroupAdd, contentDescription = "Join Canvas")
                }
                ExtendedFloatingActionButton(
                    onClick = { viewModel.handleIntent(HomeIntent.ShowNewCanvasSheet) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New Canvas") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                filteredCanvases.isEmpty() && !uiState.isSearchActive -> {
                    EmptyState(
                        onCreateCanvas = {
                            viewModel.handleIntent(HomeIntent.ShowNewCanvasSheet)
                        }
                    )
                }

                filteredCanvases.isEmpty() && uiState.isSearchActive -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No results found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try a different search term",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    CanvasGrid(
                        canvases = filteredCanvases,
                        contextMenuCanvas = uiState.contextMenuCanvas,
                        onCanvasClick = { canvas ->
                            onOpenNewCanvas(canvas.id, canvas.name, canvas.widthPx, canvas.heightPx)
                        },
                        onCanvasLongClick = { canvas ->
                            viewModel.handleIntent(HomeIntent.ShowContextMenu(canvas))
                        },
                        onDismissContextMenu = {
                            viewModel.handleIntent(HomeIntent.DismissContextMenu)
                        },
                        onCopyShareCode = { canvas ->
                            if (canvas.shareCode.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(canvas.shareCode))
                                Toast.makeText(context, "Code copied: ${canvas.shareCode}", Toast.LENGTH_SHORT).show()
                            }
                            viewModel.handleIntent(HomeIntent.DismissContextMenu)
                        },
                        onRename = { canvas ->
                            viewModel.handleIntent(HomeIntent.ShowRenameDialog(canvas))
                        },
                        onRemoveContributor = { canvas ->
                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            val toRemove = uid?.let { u -> canvas.members.keys.firstOrNull { it != u } }
                            if (uid != null && toRemove != null) {
                                viewModel.handleIntent(HomeIntent.RemoveContributor(canvas.id, toRemove))
                            }
                        },
                        onTogglePin = { canvas ->
                            viewModel.handleIntent(
                                HomeIntent.TogglePin(canvas.id, !canvas.isPinned)
                            )
                        },
                        onDelete = { canvas ->
                            viewModel.handleIntent(HomeIntent.ShowDeleteDialog(canvas))
                        }
                    )
                }
            }
        }
    }

    // New Canvas bottom sheet
    if (uiState.isNewCanvasSheetVisible) {
        NewCanvasBottomSheet(
            sheetState = sheetState,
            onDismiss = { viewModel.handleIntent(HomeIntent.DismissNewCanvasSheet) },
            onCreate = { name, width, height ->
                viewModel.handleIntent(HomeIntent.CreateCanvas(name, width, height))
            }
        )
    }

    // Join canvas dialog
    if (uiState.isJoinCanvasDialogVisible) {
        JoinCanvasDialog(
            isJoining = uiState.isJoiningCanvas,
            onJoin = { code -> viewModel.handleIntent(HomeIntent.JoinCanvas(code)) },
            onDismiss = { viewModel.handleIntent(HomeIntent.DismissJoinCanvasDialog) }
        )
    }

    // Rename dialog
    uiState.renameDialogCanvas?.let { canvas ->
        RenameDialog(
            currentName = canvas.name,
            onConfirm = { newName ->
                viewModel.handleIntent(HomeIntent.RenameCanvas(canvas.id, newName))
            },
            onDismiss = { viewModel.handleIntent(HomeIntent.DismissRenameDialog) }
        )
    }

    // Delete confirmation dialog
    uiState.deleteDialogCanvas?.let { canvas ->
        DeleteConfirmationDialog(
            canvasName = canvas.name,
            onConfirm = { viewModel.handleIntent(HomeIntent.DeleteCanvas(canvas)) },
            onDismiss = { viewModel.handleIntent(HomeIntent.DismissDeleteDialog) }
        )
    }

    if (showLogoutConfirm) {
        LogoutConfirmationDialog(
            onConfirm = {
                showLogoutConfirm = false
                viewModel.handleIntent(HomeIntent.SignOut)
            },
            onDismiss = { showLogoutConfirm = false }
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    overflowMenuExpanded: Boolean,
    onOverflowMenuExpandedChange: (Boolean) -> Unit,
    onAboutClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search canvases…") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
            }
        },
        actions = {
            HomeOverflowMenu(
                expanded = overflowMenuExpanded,
                onExpandedChange = onOverflowMenuExpandedChange,
                onAboutClick = onAboutClick,
                onLogoutClick = onLogoutClick
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                }
            }
        }
    )
}

@Composable
private fun HomeOverflowMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAboutClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text("About") },
                onClick = {
                    onExpandedChange(false)
                    onAboutClick()
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Info, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Log out") },
                onClick = {
                    onExpandedChange(false)
                    onLogoutClick()
                },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                }
            )
        }
    }
}

@Composable
private fun LogoutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log out?") },
        text = {
            Text("You will be signed out of this device. You can sign in again at any time.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Log out")
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
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About CanvasX") },
        text = {
            Text(
                "CanvasX is a collaborative drawing app. This is sample about text; " +
                    "replace it with version info, credits, or links when you are ready."
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun EmptyState(onCreateCanvas: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No canvases yet",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Tap + to create your first",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        FilledTonalButton(onClick = onCreateCanvas) {
            Text("Create Canvas")
        }
    }
}

@Composable
private fun CanvasGrid(
    canvases: List<Canvas>,
    contextMenuCanvas: Canvas?,
    onCanvasClick: (Canvas) -> Unit,
    onCanvasLongClick: (Canvas) -> Unit,
    onDismissContextMenu: () -> Unit,
    onCopyShareCode: (Canvas) -> Unit,
    onRename: (Canvas) -> Unit,
    onRemoveContributor: (Canvas) -> Unit,
    onTogglePin: (Canvas) -> Unit,
    onDelete: (Canvas) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(canvases, key = { it.id }) { canvas ->
            Box {
                CanvasCard(
                    canvas = canvas,
                    onClick = { onCanvasClick(canvas) },
                    onLongClick = { onCanvasLongClick(canvas) }
                )

                DropdownMenu(
                    expanded = contextMenuCanvas?.id == canvas.id,
                    onDismissRequest = onDismissContextMenu
                ) {
                    if (canvas.shareCode.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("Copy Share Code") },
                            onClick = { onCopyShareCode(canvas) },
                            leadingIcon = {
                                Icon(Icons.Filled.Share, contentDescription = null)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { onRename(canvas) },
                        leadingIcon = {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                        }
                    )
                    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                    val otherMemberId = currentUid?.let { uid ->
                        canvas.members.keys.firstOrNull { it != uid }
                    }
                    if (canvas.ownerId == currentUid && otherMemberId != null) {
                        DropdownMenuItem(
                            text = { Text("Remove Contributor") },
                            onClick = { onRemoveContributor(canvas) },
                            leadingIcon = {
                                Icon(Icons.Filled.PersonRemove, contentDescription = null)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(if (canvas.isPinned) "Unpin" else "Pin")
                        },
                        onClick = {
                            onTogglePin(canvas)
                            onDismissContextMenu()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (canvas.isPinned) Icons.Filled.PushPin
                                else Icons.Outlined.PushPin,
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = { onDelete(canvas) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Canvas") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Canvas name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Rename")
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
private fun DeleteConfirmationDialog(
    canvasName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $canvasName?") },
        text = { Text("This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
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
private fun JoinCanvasDialog(
    isJoining: Boolean,
    onJoin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isJoining) onDismiss() },
        title = { Text("Join Canvas") },
        text = {
            Column {
                Text(
                    text = "Enter the 6-character share code to join a canvas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it.uppercase() },
                    label = { Text("Share Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        textAlign = TextAlign.Center
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onJoin(code) },
                enabled = code.length >= 5 && !isJoining
            ) {
                if (isJoining) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Join")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isJoining) {
                Text("Cancel")
            }
        }
    )
}
