package com.my_app.art_collab.data.session

import com.google.firebase.auth.FirebaseAuth
import com.my_app.art_collab.data.local.db.dao.CanvasDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clears local canvas rows when the Firebase Auth session identity changes.
 * Room is a separate cache from Firestore/RTDB; wiping the cloud does not clear the device DB.
 *
 * Uses an externally provided [CoroutineScope] (bound to the app's process lifecycle via Hilt)
 * instead of creating an unstructured CoroutineScope.
 */
@Singleton
class UserSessionCleaner @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val canvasDao: CanvasDao,
    @ApplicationScope private val scope: CoroutineScope,
) {
    init {
        var lastUid: String? = firebaseAuth.currentUser?.uid
        firebaseAuth.addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid == lastUid) return@addAuthStateListener
            lastUid = uid
            scope.launch {
                canvasDao.deleteAllCanvases()
            }
        }
    }
}
