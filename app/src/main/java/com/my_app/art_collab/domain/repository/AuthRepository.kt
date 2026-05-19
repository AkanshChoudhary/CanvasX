package com.my_app.art_collab.domain.repository

interface AuthRepository {
    suspend fun signInWithGoogle(idToken: String): Result<Unit>
    fun isUserAuthenticated(): Boolean
    fun getCurrentUserId(): String?
    suspend fun signOut()
    suspend fun reauthenticate(idToken: String)
    suspend fun deleteUserDocument(userId: String)
    suspend fun deleteAccount()
}
