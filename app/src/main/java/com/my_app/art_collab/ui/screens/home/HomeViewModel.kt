package com.my_app.art_collab.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.my_app.art_collab.domain.model.Canvas
import com.my_app.art_collab.domain.usecase.canvas.CreateCanvasUseCase
import com.my_app.art_collab.domain.usecase.canvas.DeleteCanvasUseCase
import com.my_app.art_collab.domain.usecase.canvas.GetCanvasListUseCase
import com.my_app.art_collab.domain.usecase.canvas.JoinCanvasUseCase
import com.my_app.art_collab.domain.usecase.canvas.SyncCanvasesUseCase
import com.my_app.art_collab.domain.usecase.canvas.UpdateCanvasUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCanvasListUseCase: GetCanvasListUseCase,
    private val createCanvasUseCase: CreateCanvasUseCase,
    private val deleteCanvasUseCase: DeleteCanvasUseCase,
    private val updateCanvasUseCase: UpdateCanvasUseCase,
    private val syncCanvasesUseCase: SyncCanvasesUseCase,
    private val joinCanvasUseCase: JoinCanvasUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    val filteredCanvases: StateFlow<List<Canvas>> = combine(
        _uiState.map { it.canvases },
        _uiState.map { it.searchQuery }
    ) { canvases, query ->
        if (query.isBlank()) canvases
        else canvases.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        syncCanvasesUseCase()
        viewModelScope.launch {
            getCanvasListUseCase().collect { canvases ->
                _uiState.update { it.copy(canvases = canvases, isLoading = false) }
            }
        }
    }

    fun handleIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.SetSearchQuery -> {
                _uiState.update { it.copy(searchQuery = intent.query) }
            }

            is HomeIntent.SetSearchActive -> {
                _uiState.update {
                    it.copy(
                        isSearchActive = intent.active,
                        searchQuery = if (!intent.active) "" else it.searchQuery
                    )
                }
            }

            is HomeIntent.CreateCanvas -> {
                viewModelScope.launch {
                    try {
                        val now = System.currentTimeMillis()
                        val canvasId = UUID.randomUUID().toString()
                        val canvas = Canvas(
                            id = canvasId,
                            ownerId = firebaseAuth.currentUser?.uid ?: "",
                            name = intent.name.ifBlank { "Untitled Canvas" },
                            widthPx = intent.widthPx,
                            heightPx = intent.heightPx,
                            createdAt = now,
                            updatedAt = now
                        )
                        createCanvasUseCase(canvas)
                        _uiState.update {
                            it.copy(isNewCanvasSheetVisible = false,newCanvasId = canvasId)
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = e.message) }
                    }
                }
            }

            is HomeIntent.DeleteCanvas -> {
                viewModelScope.launch {
                    try {
                        deleteCanvasUseCase(intent.canvas)
                        _uiState.update { it.copy(deleteDialogCanvas = null) }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = e.message) }
                    }
                }
            }

            is HomeIntent.TogglePin -> {
                viewModelScope.launch {
                    try {
                        updateCanvasUseCase.updatePinned(intent.canvasId, intent.isPinned)
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = e.message) }
                    }
                }
            }

            is HomeIntent.RenameCanvas -> {
                viewModelScope.launch {
                    try {
                        updateCanvasUseCase.rename(intent.canvasId, intent.newName)
                        _uiState.update { it.copy(renameDialogCanvas = null) }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = e.message) }
                    }
                }
            }

            is HomeIntent.DuplicateCanvas -> {
                viewModelScope.launch {
                    try {
                        val now = System.currentTimeMillis()
                        val copy = intent.canvas.copy(
                            id = UUID.randomUUID().toString(),
                            name = "${intent.canvas.name} Copy",
                            createdAt = now,
                            updatedAt = now,
                            thumbnailLocalPath = null
                        )
                        createCanvasUseCase(copy)
                        _uiState.update { it.copy(contextMenuCanvas = null) }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = e.message) }
                    }
                }
            }

            is HomeIntent.ShowContextMenu -> {
                _uiState.update { it.copy(contextMenuCanvas = intent.canvas) }
            }

            is HomeIntent.DismissContextMenu -> {
                _uiState.update { it.copy(contextMenuCanvas = null) }
            }

            is HomeIntent.ShowRenameDialog -> {
                _uiState.update {
                    it.copy(renameDialogCanvas = intent.canvas, contextMenuCanvas = null)
                }
            }

            is HomeIntent.DismissRenameDialog -> {
                _uiState.update { it.copy(renameDialogCanvas = null) }
            }

            is HomeIntent.ShowDeleteDialog -> {
                _uiState.update {
                    it.copy(deleteDialogCanvas = intent.canvas, contextMenuCanvas = null)
                }
            }

            is HomeIntent.DismissDeleteDialog -> {
                _uiState.update { it.copy(deleteDialogCanvas = null) }
            }

            is HomeIntent.ShowNewCanvasSheet -> {
                _uiState.update { it.copy(isNewCanvasSheetVisible = true) }
            }

            is HomeIntent.DismissNewCanvasSheet -> {
                _uiState.update { it.copy(isNewCanvasSheetVisible = false) }
            }

            is HomeIntent.ShowJoinCanvasDialog -> {
                _uiState.update { it.copy(isJoinCanvasDialogVisible = true) }
            }

            is HomeIntent.DismissJoinCanvasDialog -> {
                _uiState.update { it.copy(isJoinCanvasDialogVisible = false) }
            }

            is HomeIntent.JoinCanvas -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isJoiningCanvas = true) }
                    try {
                        val userId = firebaseAuth.currentUser?.uid ?: ""
                        val canvas = joinCanvasUseCase(intent.shareCode, userId)
                        _uiState.update {
                            it.copy(
                                isJoinCanvasDialogVisible = false,
                                isJoiningCanvas = false,
                                joinedCanvasId = canvas.id
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                isJoinCanvasDialogVisible = false,
                                isJoiningCanvas = false,
                                error = e.message ?: "Failed to join canvas"
                            )
                        }
                    }
                }
            }

            is HomeIntent.ClearError -> {
                _uiState.update { it.copy(error = null) }
            }
        }
    }
}

