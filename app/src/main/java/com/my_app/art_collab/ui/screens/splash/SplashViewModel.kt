package com.my_app.art_collab.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my_app.art_collab.domain.repository.AuthRepository
import com.my_app.art_collab.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _startDestination = MutableStateFlow<Screen>(Screen.Auth)
    val startDestination = _startDestination.asStateFlow()

    init {
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            // Simulate a small delay or perform checks if necessary
            // For example, refreshing tokens if needed, though Firebase handles this internally usually.
            // Just checking if currentUser is not null is synchronous and fast, but let's keep it async-ready.
            // Also good to ensure we don't flash if the check is instant.
            
            val isAuthenticated = authRepository.isUserAuthenticated()
            _startDestination.value = if (isAuthenticated) Screen.Home else Screen.Auth
            
            // Artificial delay to show splash a bit longer if checks are too fast (optional, removed for now per "speed" preference)
            // delay(500) 
            
            _isLoading.value = false
        }
    }
}
