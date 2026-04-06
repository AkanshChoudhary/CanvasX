package com.my_app.art_collab.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.my_app.art_collab.domain.repository.AuthRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth
) : AuthRepository {

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null
    }
}
