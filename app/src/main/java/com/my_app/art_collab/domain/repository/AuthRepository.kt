package com.my_app.art_collab.domain.repository

interface AuthRepository {
    suspend fun signInWithGoogle(idToken: String): Result<Unit>
    fun isUserAuthenticated(): Boolean
    suspend fun signOut()
}
