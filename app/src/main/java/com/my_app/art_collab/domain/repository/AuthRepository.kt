package com.my_app.art_collab.domain.repository

import android.content.Intent

interface AuthRepository {
    suspend fun signInWithGoogle(idToken: String): Result<Unit>
    fun isUserAuthenticated(): Boolean
}
