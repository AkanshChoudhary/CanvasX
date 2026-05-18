package com.my_app.art_collab.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import com.my_app.art_collab.data.image.CanvasThumbnailStore
import com.my_app.art_collab.data.local.db.dao.CanvasDao
import com.my_app.art_collab.data.local.db.entity.CanvasEntity
import com.my_app.art_collab.data.local.db.toDomain
import com.my_app.art_collab.data.local.db.toEntity
import com.my_app.art_collab.domain.model.Canvas
import com.my_app.art_collab.domain.model.CanvasMember
import com.my_app.art_collab.domain.repository.CanvasRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class CanvasRepositoryImpl @Inject constructor(
    private val canvasDao: CanvasDao,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val thumbnailStore: CanvasThumbnailStore,
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
        val now = maxOf(canvas.createdAt, canvas.updatedAt, System.currentTimeMillis())
        val ownerId = canvas.ownerId
        val members = ensureOwnerMembership(canvas.members, ownerId, now)
        val canvasWithCode = canvas.copy(
            shareCode = code,
            members = members,
            createdAt = if (canvas.createdAt == 0L) now else canvas.createdAt,
            updatedAt = now,
        )

        canvasDao.upsertCanvas(canvasWithCode.toEntity())

        val batch = firestore.batch()
        val canvasRef = firestore.collection(COL_CANVASES).document(canvasWithCode.id)
        batch.set(canvasRef, canvasToFirestoreMap(canvasWithCode))
        batch.set(
            libraryDoc(ownerId, canvasWithCode.id),
            libraryDocData(canvasWithCode, roleForUser(ownerId, members), members),
            SetOptions.merge()
        )
        batch.commit().await()
    }

    override suspend fun updateCanvas(canvas: Canvas) {
        canvasDao.upsertCanvas(canvas.toEntity())
        val batch = firestore.batch()
        val canvasRef = firestore.collection(COL_CANVASES).document(canvas.id)
        val merged = canvasToFirestoreMap(canvas).apply {
            // Merge would otherwise keep legacy `collaboratorIds` on upgraded documents.
            put("collaboratorIds", FieldValue.delete())
        }
        batch.set(canvasRef, merged, SetOptions.merge())
        fanOutLibraryMetadata(batch, canvas, canvas.members)
        batch.commit().await()
    }

    override suspend fun deleteCanvas(canvas: Canvas) {
        try {
            thumbnailStore.deleteThumbnail(canvas.id)
        } catch (_: Exception) {
        }
        canvasDao.deleteCanvas(canvas.toEntity())
        val canvasId = canvas.id
        val snap = firestore.collection(COL_CANVASES).document(canvasId).get().await()
        val memberUids = if (canvas.members.isNotEmpty()) {
            canvas.members.keys.toList()
        } else {
            membersFromCanvasDoc(snap).keys.toList()
        }
        val batch = firestore.batch()
        batch.delete(firestore.collection(COL_CANVASES).document(canvasId))
        memberUids.forEach { uid ->
            batch.delete(libraryDoc(uid, canvasId))
        }
        batch.commit().await()
    }

    override suspend fun updatePinned(canvasId: String, isPinned: Boolean) {
        canvasDao.updatePinned(canvasId, isPinned)
    }

    override suspend fun renameCanvas(canvasId: String, newName: String) {
        val updatedAt = System.currentTimeMillis()
        canvasDao.updateName(canvasId, newName, updatedAt)

        val snap = firestore.collection(COL_CANVASES).document(canvasId).get().await()
        if (!snap.exists()) return
        val members = membersFromCanvasDoc(snap)
        val batch = firestore.batch()
        batch.update(
            firestore.collection(COL_CANVASES).document(canvasId),
            mapOf(
                "name" to newName,
                "updatedAt" to updatedAt,
                "collaboratorIds" to FieldValue.delete(),
            )
        )
        members.keys.forEach { uid ->
            batch.update(
                libraryDoc(uid, canvasId),
                mapOf("title" to newName, "updatedAt" to updatedAt)
            )
        }
        batch.commit().await()
    }

    override suspend fun joinCanvasByCode(shareCode: String, userId: String): Canvas {
        val snapshot = firestore.collection(COL_CANVASES)
            .whereEqualTo("shareCode", shareCode)
            .limit(1)
            .get()
            .await()

        val doc = snapshot.documents.firstOrNull()
            ?: throw IllegalArgumentException("No canvas found with code $shareCode")

        val canvasId = doc.id
        val members = membersFromCanvasDoc(doc).toMutableMap()

        if (userId !in members && members.size >= MAX_MEMBERS) {
            throw IllegalStateException("Max limit reached (2 collaborators)")
        }

        val joinTime = System.currentTimeMillis()
        if (userId !in members) {
            members[userId] = CanvasMember(CanvasMember.ROLE_EDITOR, joinTime)
        }

        val updatedAt = joinTime
        val canvas = Canvas(
            id = canvasId,
            ownerId = doc.getString("ownerId") ?: "",
            name = doc.getString("name") ?: "Untitled",
            widthPx = doc.getLong("widthPx")?.toInt() ?: 1080,
            heightPx = doc.getLong("heightPx")?.toInt() ?: 1920,
            members = members,
            shareCode = doc.getString("shareCode") ?: "",
            createdAt = doc.getLong("createdAt") ?: 0,
            updatedAt = updatedAt,
        )

        val batch = firestore.batch()
        val canvasRef = firestore.collection(COL_CANVASES).document(canvasId)
        batch.set(
            canvasRef,
            mapOf(
                "members" to membersToFirestoreMap(members),
                "updatedAt" to updatedAt,
                "schemaVersion" to SCHEMA_VERSION,
                "collaboratorIds" to FieldValue.delete(),
            ),
            SetOptions.merge()
        )

        members.keys.forEach { uid ->
            batch.set(
                libraryDoc(uid, canvasId),
                libraryDocData(canvas, roleForUser(uid, members), members),
                SetOptions.merge()
            )
        }

        batch.commit().await()
        canvasDao.upsertCanvas(canvas.toEntity())
        return canvas
    }

    override suspend fun removeCollaboratorFromCanvas(canvasId: String, userIdToRemove: String) {
        val snap = firestore.collection(COL_CANVASES).document(canvasId).get().await()
        if (!snap.exists()) return
        val members = membersFromCanvasDoc(snap).toMutableMap()
        members.remove(userIdToRemove)
        val updatedAt = System.currentTimeMillis()

        val canvas = Canvas(
            id = canvasId,
            ownerId = snap.getString("ownerId") ?: "",
            name = snap.getString("name") ?: "Untitled",
            widthPx = snap.getLong("widthPx")?.toInt() ?: 1080,
            heightPx = snap.getLong("heightPx")?.toInt() ?: 1920,
            members = members,
            shareCode = snap.getString("shareCode") ?: "",
            createdAt = snap.getLong("createdAt") ?: 0,
            updatedAt = updatedAt,
        )

        val batch = firestore.batch()
        batch.update(
            firestore.collection(COL_CANVASES).document(canvasId),
            mapOf(
                "members.$userIdToRemove" to FieldValue.delete(),
                "updatedAt" to updatedAt,
                "collaboratorIds" to FieldValue.delete(),
            )
        )
        batch.delete(libraryDoc(userIdToRemove, canvasId))
        members.keys.forEach { uid ->
            batch.update(
                libraryDoc(uid, canvasId),
                mapOf(
                    "memberIds" to memberIdsSorted(members),
                    "updatedAt" to updatedAt,
                )
            )
        }
        batch.commit().await()
        canvasDao.upsertCanvas(canvas.toEntity())
    }

    override suspend fun transferOwnershipAndLeave(
        canvasId: String,
        oldOwnerId: String,
        newOwnerId: String
    ) {
        val snap = firestore.collection(COL_CANVASES).document(canvasId).get().await()
        if (!snap.exists()) return

        val members = membersFromCanvasDoc(snap).toMutableMap()
        members.remove(oldOwnerId)
        members[newOwnerId] = CanvasMember(CanvasMember.ROLE_OWNER, members[newOwnerId]?.joinedAt ?: System.currentTimeMillis())

        val updatedAt = System.currentTimeMillis()

        val batch = firestore.batch()
        val canvasRef = firestore.collection(COL_CANVASES).document(canvasId)
        batch.update(
            canvasRef,
            mapOf(
                "ownerId" to newOwnerId,
                "members" to membersToFirestoreMap(members),
                "updatedAt" to updatedAt,
                "collaboratorIds" to FieldValue.delete(),
            )
        )
        batch.delete(libraryDoc(oldOwnerId, canvasId))
        members.keys.forEach { uid ->
            val canvas = Canvas(
                id = canvasId,
                ownerId = newOwnerId,
                name = snap.getString("name") ?: "Untitled",
                widthPx = snap.getLong("widthPx")?.toInt() ?: 1080,
                heightPx = snap.getLong("heightPx")?.toInt() ?: 1920,
                members = members,
                shareCode = snap.getString("shareCode") ?: "",
                createdAt = snap.getLong("createdAt") ?: 0,
                updatedAt = updatedAt,
            )
            batch.set(
                libraryDoc(uid, canvasId),
                libraryDocData(canvas, roleForUser(uid, members), members),
                SetOptions.merge()
            )
        }
        batch.commit().await()
        canvasDao.deleteCanvasById(canvasId)
    }

    override suspend fun updateThumbnail(canvasId: String, absolutePath: String) {
        canvasDao.updateThumbnail(canvasId, absolutePath)
    }

    override fun syncCanvasesFromRemote() {
        val userId = firebaseAuth.currentUser?.uid ?: return
        val libraryColl = firestore.collection(COL_USERS).document(userId).collection(COL_LIBRARY)

        // Prefer server truth so wiping the project in console is reflected even when the
        // Firestore SDK still has an on-disk cache of old library docs.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val serverSnap = libraryColl.get(Source.SERVER).await()
                applyLibraryDocumentsToRoom(serverSnap.documents)
            } catch (_: Exception) {
                // Offline, permission, etc.: snapshot listener still applies cache/server later.
            }
        }

        libraryColl.addSnapshotListener { snapshots, error ->
            if (error != null || snapshots == null) return@addSnapshotListener
            CoroutineScope(Dispatchers.IO).launch {
                applyLibraryDocumentsToRoom(snapshots.documents)
            }
        }
    }

    private suspend fun applyLibraryDocumentsToRoom(documents: List<DocumentSnapshot>) {
        if (documents.isEmpty()) {
            canvasDao.deleteAllCanvases()
            return
        }
        val remoteIds = mutableListOf<String>()
        for (doc in documents) {
            val canvasId = doc.id
            remoteIds.add(canvasId)
            val existingEntity = canvasDao.getCanvas(canvasId)
            val ownerId = doc.getString("ownerId") ?: ""
            @Suppress("UNCHECKED_CAST")
            val memberIds = (doc.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val members = membersFromLibraryMemberIds(ownerId, memberIds, doc.getLong("createdAt") ?: 0L)
            val entity = CanvasEntity(
                id = canvasId,
                ownerId = ownerId,
                name = doc.getString("title") ?: "Untitled",
                widthPx = doc.getLong("widthPx")?.toInt() ?: 1080,
                heightPx = doc.getLong("heightPx")?.toInt() ?: 1920,
                membersJson = Json.encodeToString(members),
                shareCode = doc.getString("shareCode") ?: "",
                isViewOnly = existingEntity?.isViewOnly ?: false,
                isPinned = existingEntity?.isPinned ?: false,
                updatedAt = doc.getLong("updatedAt") ?: 0,
                createdAt = doc.getLong("createdAt") ?: 0,
                thumbnailLocalPath = existingEntity?.thumbnailLocalPath ?: ""
            )
            canvasDao.upsertCanvas(entity)
        }
        canvasDao.deleteCanvasesNotIn(remoteIds)
    }

    private fun libraryDoc(uid: String, canvasId: String) =
        firestore.collection(COL_USERS).document(uid).collection(COL_LIBRARY).document(canvasId)

    private fun fanOutLibraryMetadata(batch: WriteBatch, canvas: Canvas, members: Map<String, CanvasMember>) {
        members.keys.forEach { uid ->
            batch.set(
                libraryDoc(uid, canvas.id),
                libraryDocData(canvas, roleForUser(uid, members), members),
                SetOptions.merge()
            )
        }
    }

    private fun generateShareCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun canvasToFirestoreMap(canvas: Canvas): HashMap<String, Any?> = hashMapOf(
        "name" to canvas.name,
        "ownerId" to canvas.ownerId,
        "widthPx" to canvas.widthPx,
        "heightPx" to canvas.heightPx,
        "members" to membersToFirestoreMap(canvas.members),
        "shareCode" to canvas.shareCode,
        "updatedAt" to canvas.updatedAt,
        "createdAt" to canvas.createdAt,
        "schemaVersion" to SCHEMA_VERSION,
    )

    companion object {
        private const val COL_CANVASES = "canvases"
        private const val COL_USERS = "users"
        private const val COL_LIBRARY = "library"
        private const val SCHEMA_VERSION = 1
        private const val MAX_MEMBERS = 2
    }
}

private fun ensureOwnerMembership(
    members: Map<String, CanvasMember>,
    ownerId: String,
    joinedAt: Long
): Map<String, CanvasMember> {
    if (ownerId.isBlank()) return members
    if (ownerId in members) {
        val existing = members[ownerId]!!
        if (existing.role == CanvasMember.ROLE_OWNER) return members
        return members + (ownerId to existing.copy(role = CanvasMember.ROLE_OWNER))
    }
    return members + (ownerId to CanvasMember(CanvasMember.ROLE_OWNER, joinedAt))
}

private fun roleForUser(uid: String, members: Map<String, CanvasMember>): String =
    members[uid]?.role ?: CanvasMember.ROLE_EDITOR

private fun memberIdsSorted(members: Map<String, CanvasMember>): List<String> =
    members.keys.sorted()

private fun membersToFirestoreMap(members: Map<String, CanvasMember>): Map<String, Map<String, Any>> =
    members.mapValues { (_, m) ->
        mapOf(
            "role" to m.role,
            "joinedAt" to m.joinedAt,
        )
    }

private fun libraryDocData(
    canvas: Canvas,
    role: String,
    members: Map<String, CanvasMember>,
): Map<String, Any?> = mapOf(
    "canvasId" to canvas.id,
    "ownerId" to canvas.ownerId,
    "title" to canvas.name,
    "widthPx" to canvas.widthPx.toLong(),
    "heightPx" to canvas.heightPx.toLong(),
    "shareCode" to canvas.shareCode,
    "updatedAt" to canvas.updatedAt,
    "createdAt" to canvas.createdAt,
    "role" to role,
    "memberIds" to memberIdsSorted(members),
    "schemaVersion" to 1,
)

private fun membersFromLibraryMemberIds(
    ownerId: String,
    memberIds: List<String>,
    fallbackJoinedAt: Long
): Map<String, CanvasMember> {
    if (memberIds.isEmpty() && ownerId.isNotBlank()) {
        return mapOf(ownerId to CanvasMember(CanvasMember.ROLE_OWNER, fallbackJoinedAt))
    }
    return memberIds.associateWith { uid ->
        if (uid == ownerId) CanvasMember(CanvasMember.ROLE_OWNER, fallbackJoinedAt)
        else CanvasMember(CanvasMember.ROLE_EDITOR, 0L)
    }
}

@Suppress("UNCHECKED_CAST")
private fun membersFromCanvasDoc(doc: DocumentSnapshot): Map<String, CanvasMember> {
    val raw = doc.get("members") as? Map<String, Any?>
    if (raw != null && raw.isNotEmpty()) {
        return raw.mapNotNull { (uid, v) ->
            val m = v as? Map<*, *> ?: return@mapNotNull null
            val role = m["role"] as? String ?: CanvasMember.ROLE_EDITOR
            val joinedAt = (m["joinedAt"] as? Number)?.toLong() ?: 0L
            uid to CanvasMember(role, joinedAt)
        }.toMap()
    }
    val ownerId = doc.getString("ownerId") ?: ""
    val legacy = doc.get("collaboratorIds") as? List<*>
    if (legacy != null) {
        return legacy.mapNotNull { it as? String }.associateWith { uid ->
            if (uid == ownerId) CanvasMember(CanvasMember.ROLE_OWNER, doc.getLong("createdAt") ?: 0L)
            else CanvasMember(CanvasMember.ROLE_EDITOR, 0L)
        }
    }
    return emptyMap()
}
