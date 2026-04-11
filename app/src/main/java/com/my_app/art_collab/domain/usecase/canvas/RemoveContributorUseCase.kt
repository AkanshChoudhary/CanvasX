package com.my_app.art_collab.domain.usecase.canvas

import com.my_app.art_collab.domain.repository.CanvasRepository
import javax.inject.Inject

class RemoveContributorUseCase @Inject constructor(
    private val canvasRepository: CanvasRepository
){
    suspend operator fun invoke(canvasId: String, userIdToRemove: String){
        canvasRepository.removeCollaboratorFromCanvas(canvasId, userIdToRemove)
    }

}