package com.my_app.art_collab.domain.usecase.auth

import com.my_app.art_collab.domain.repository.AuthRepository
import com.my_app.art_collab.domain.repository.CanvasRepository
import com.my_app.art_collab.domain.repository.RealtimeDBRepository
import com.my_app.art_collab.domain.repository.StorageRepository
import javax.inject.Inject

class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val canvasRepository: CanvasRepository,
    private val storageRepository: StorageRepository,
    private val realtimeDBRepository: RealtimeDBRepository,
) {
    suspend operator fun invoke(
        canvases: List<com.my_app.art_collab.domain.model.Canvas>,
        idToken: String,
    ) {
        val userId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("No user signed in")

        authRepository.reauthenticate(idToken)

        for (canvas in canvases) {
            val isOwner = canvas.ownerId == userId
            val collaboratorIds = canvas.members.keys.filter { it != userId }
            val hasCollaborator = collaboratorIds.isNotEmpty()

            when {
                isOwner && hasCollaborator -> {
                    realtimeDBRepository.deleteCanvasOps(canvas.id)
                    val newOwnerId = collaboratorIds.first()
                    canvasRepository.transferOwnershipAndLeave(canvas.id, userId, newOwnerId)
                }
                isOwner && !hasCollaborator -> {
                    try { storageRepository.deleteAllCanvasFiles(canvas.id) } catch (_: Exception) {}
                    realtimeDBRepository.deleteCanvasSubtree(canvas.id)
                    canvasRepository.deleteCanvas(canvas)
                }
                else -> {
                    realtimeDBRepository.deleteCanvasOps(canvas.id)
                    canvasRepository.removeCollaboratorFromCanvas(canvas.id, userId)
                }
            }
        }

        authRepository.deleteUserDocument(userId)
        authRepository.deleteAccount()
    }
}
