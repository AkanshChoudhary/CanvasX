package com.my_app.art_collab.ui.screens.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.my_app.art_collab.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    val result = authRepository.signInWithGoogle(idToken)
                    if (result.isSuccess) {
                        _uiState.update { it.copy(isLoading = false, isAuthenticated = true) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
                    }
                } else {
                     _uiState.update { it.copy(isLoading = false, error = "Google Sign-In failed: No ID Token") }
                }

            } catch (e: ApiException) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Sign-in failed") }
            }
        }
    }
    
    fun clearError() {
         _uiState.update { it.copy(error = null) }
    }
}
