package com.my_app.art_collab.ui.screens.auth

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.my_app.art_collab.R

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) {
            onAuthSuccess()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.handleGoogleSignInResult(result.data)
        } else {
             // Handle cancellation or failure if needed, maybe show a snackbar
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(120.dp)) // Approx 20% of screen height

            // Logo
            Image(
                // Use foreground vector drawable instead of adaptive icon mipmap to avoid crash
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "CanvasX Logo",
                modifier = Modifier.size(80.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "CanvasX",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Create together.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            val clientId = stringResource(R.string.default_web_client_id)
            FilledTonalButton(
                onClick = {
                    try {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(clientId)
                            .requestEmail()
                            .build()
                        val googleSignInClient = GoogleSignIn.getClient(context, gso)
                        launcher.launch(googleSignInClient.signInIntent)
                    } catch (e: Exception) {
                        Log.e("AuthScreen", "Error launching Google Sign-In", e)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isLoading
            ) {
                 if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(text = "Continue with Google")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Using deprecated ClickableText for simplicity as LinkAnnotation is experimental/new in strict 1.7 logic
            // To be robust, sticking to ClickableText and suppressing deprecation is safer for now if compiler is strict.
            @Suppress("DEPRECATION")
            val annotatedString = buildAnnotatedString {
                append("By continuing you agree to our ")
                withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline, color = MaterialTheme.colorScheme.primary)) {
                    pushStringAnnotation(tag = "TERMS", annotation = "https://example.com/terms")
                    append("Terms")
                    pop()
                }
                append(" and ")
                withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline, color = MaterialTheme.colorScheme.primary)) {
                    pushStringAnnotation(tag = "PRIVACY", annotation = "https://example.com/privacy")
                    append("Privacy Policy")
                    pop()
                }
            }

            @Suppress("DEPRECATION")
            ClickableText(
                text = annotatedString,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                ),
                onClick = { offset ->
                    annotatedString.getStringAnnotations(tag = "TERMS", start = offset, end = offset).firstOrNull()?.let {
                        // Open Terms URL
                    }
                    annotatedString.getStringAnnotations(tag = "PRIVACY", start = offset, end = offset).firstOrNull()?.let {
                        // Open Privacy URL
                    }
                }
            )

             Spacer(modifier = Modifier.padding(WindowInsets.navigationBars.asPaddingValues()))
             Spacer(modifier = Modifier.height(32.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
