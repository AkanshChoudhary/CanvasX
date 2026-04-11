package com.my_app.art_collab.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.my_app.art_collab.data.local.db.dao.CanvasDao
import com.my_app.art_collab.data.local.db.toDomain
import com.my_app.art_collab.data.local.db.toEntity
import com.my_app.art_collab.domain.model.Canvas
import com.my_app.art_collab.domain.repository.CanvasRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.my_app.art_collab.data.local.db.entity.CanvasEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CanvasRepositoryImpl @Inject constructor(
    private val canvasDao: CanvasDao,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : CanvasRepository {

    override fun observeAllCanvases(): Flow<List<Canvas>> {
        return canvasDao.observeAllCanvases().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCanvas(canvasId: String): Canvas? {
        return canvasDao.getCanvas(canvasId)?.toDomain()
    }

    override suspend fun createCanvas(canvas: Canvas) {
        val code = if (canvas.shareCode.isNotBlank()) canvas.shareCode else generateShareCode()
        val collabIds = if (canvas.ownerId.isNotBlank() && canvas.ownerId !in canvas.collaboratorIds) {
            canvas.collaboratorIds + canvas.ownerId
        } else {
            canvas.collaboratorIds
        }
        val canvasWithCode = canvas.copy(shareCode = code, collaboratorIds = collabIds)

        canvasDao.upsertCanvas(canvasWithCode.toEntity())
        val canvasMap = hashMapOf(
            "name" to canvasWithCode.name,
            "ownerId" to canvasWithCode.ownerId,
            "widthPx" to canvasWithCode.widthPx,
            "heightPx" to canvasWithCode.heightPx,
            "collaboratorIds" to canvasWithCode.collaboratorIds,
            "shareCode" to code,
            "updatedAt" to canvasWithCode.updatedAt,
            "createdAt" to canvasWithCode.createdAt,
        )
        firestore.collection("canvases").document(canvasWithCode.id).set(canvasMap)
    }

    override suspend fun updateCanvas(canvas: Canvas) {
        canvasDao.upsertCanvas(canvas.toEntity())

        val canvasMap = hashMapOf(
            "name" to canvas.name,
            "ownerId" to canvas.ownerId,
            "widthPx" to canvas.widthPx,
            "heightPx" to canvas.heightPx,
            "collaboratorIds" to canvas.collaboratorIds,
            "shareCode" to canvas.shareCode,
            "updatedAt" to canvas.updatedAt,
            "createdAt" to canvas.createdAt,
        )
        firestore.collection("canvases").document(canvas.id).set(canvasMap)
    }

    override suspend fun deleteCanvas(canvas: Canvas) {
        canvasDao.deleteCanvas(canvas.toEntity())
        firestore.collection("canvases").document(canvas.id).delete().await()
    }

    override suspend fun updatePinned(canvasId: String, isPinned: Boolean) {
        canvasDao.updatePinned(canvasId, isPinned)
    }

    override suspend fun renameCanvas(canvasId: String, newName: String) {
        val updatedAt = System.currentTimeMillis()
        canvasDao.updateName(canvasId, newName, updatedAt)

        firestore.collection("canvases")
            .document(canvasId)
            .update("name", newName, "updatedAt", updatedAt)
    }

    override suspend fun joinCanvasByCode(shareCode: String, userId: String): Canvas {
        val snapshot = firestore.collection("canvases")
            .whereEqualTo("shareCode", shareCode)
            .limit(1)
            .get()
            .await()

        val doc = snapshot.documents.firstOrNull()
            ?: throw IllegalArgumentException("No canvas found with code $shareCode")

        val canvasId = doc.id

        @Suppress("UNCHECKED_CAST")
        val collabIds = (doc.get("collaboratorIds") as? List<String>) ?: emptyList()
        // collaboratorIds includes the owner (see createCanvas). Max editors = 2 → max array size 2.
        if (userId !in collabIds && collabIds.size >= 2) {
            throw IllegalStateException("Max limit reached (2 collaborators)")
        }

        firestore.collection("canvases").document(canvasId)
            .update("collaboratorIds", FieldValue.arrayUnion(userId))
            .await()
        val updatedCollabs = if (userId in collabIds) collabIds else collabIds + userId

        val canvas = Canvas(
            id = canvasId,
            ownerId = doc.getString("ownerId") ?: "",
            name = doc.getString("name") ?: "Untitled",
            widthPx = doc.getLong("widthPx")?.toInt() ?: 1080,
            heightPx = doc.getLong("heightPx")?.toInt() ?: 1920,
            collaboratorIds = updatedCollabs,
            shareCode = doc.getString("shareCode") ?: "",
            createdAt = doc.getLong("createdAt") ?: 0,
            updatedAt = doc.getLong("updatedAt") ?: 0,
        )

        canvasDao.upsertCanvas(canvas.toEntity())
        return canvas
    }

    override suspend fun removeCollaboratorFromCanvas(canvasId: String,userIdToRemove: String) {
        firestore.collection("canvases")
            .document(canvasId)
            .update("collaboratorIds",
                FieldValue.arrayRemove(userIdToRemove)).await()

    }

    override fun syncCanvasesFromRemote() {
        val userId = firebaseAuth.currentUser?.uid ?: return

        firestore.collection("canvases")
            .whereArrayContains("collaboratorIds", userId)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener

                CoroutineScope(Dispatchers.IO).launch {
                    val remoteIds = mutableListOf<String>()

                    for (doc in snapshots.documents) {
                        val canvasId = doc.id
                        remoteIds.add(canvasId)

                        val existingEntity = canvasDao.getCanvas(canvasId)

                        @Suppress("UNCHECKED_CAST")
                        val collabIds = (doc.get("collaboratorIds") as? List<String>) ?: emptyList()

                        val entity = CanvasEntity(
                            id = canvasId,
                            ownerId = doc.getString("ownerId") ?: "",
                            name = doc.getString("name") ?: "Untitled",
                            widthPx = doc.getLong("widthPx")?.toInt() ?: 1080,
                            heightPx = doc.getLong("heightPx")?.toInt() ?: 1920,
                            collaboratorIdsJson = Json.encodeToString(collabIds),
                            shareCode = doc.getString("shareCode") ?: "",
                            isViewOnly = existingEntity?.isViewOnly ?: false,
                            isPinned = existingEntity?.isPinned ?: false,
                            updatedAt = doc.getLong("updatedAt") ?: 0,
                            createdAt = doc.getLong("createdAt") ?: 0,
                            thumbnailLocalPath = existingEntity?.thumbnailLocalPath ?: ""
                        )
                        canvasDao.upsertCanvas(entity)
                    }

                    if (remoteIds.isNotEmpty()) {
                        canvasDao.deleteCanvasesNotIn(remoteIds)
                    }
                }
            }
    }

    private fun generateShareCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}


