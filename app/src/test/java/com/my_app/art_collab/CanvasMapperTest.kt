package com.my_app.art_collab

import com.my_app.art_collab.data.local.db.entity.CanvasEntity
import com.my_app.art_collab.data.local.db.toDomain
import com.my_app.art_collab.data.local.db.toEntity
import com.my_app.art_collab.domain.model.Canvas
import com.my_app.art_collab.domain.model.CanvasMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasMapperTest {
    @Test
    fun canvasEntityRoundTripPreservesShareAndMembershipState() {
        val canvas = Canvas(
            id = "canvas-1",
            ownerId = "owner-1",
            name = "Launch Poster",
            widthPx = 2048,
            heightPx = 2048,
            members = mapOf(
                "owner-1" to CanvasMember(
                    role = CanvasMember.ROLE_OWNER,
                    joinedAt = 1_700_000_000_000
                ),
                "viewer-1" to CanvasMember(
                    role = CanvasMember.ROLE_EDITOR,
                    joinedAt = 1_700_000_005_000
                )
            ),
            shareCode = "ABCD12",
            isViewOnly = false,
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_010_000,
            thumbnailLocalPath = "/tmp/canvas-thumb.png",
            isPinned = true
        )

        val restored = canvas.toEntity().toDomain()

        assertEquals(canvas.id, restored.id)
        assertEquals(canvas.ownerId, restored.ownerId)
        assertEquals(canvas.name, restored.name)
        assertEquals(canvas.widthPx, restored.widthPx)
        assertEquals(canvas.heightPx, restored.heightPx)
        assertEquals(canvas.shareCode, restored.shareCode)
        assertEquals(canvas.members, restored.members)
        assertEquals(canvas.thumbnailLocalPath, restored.thumbnailLocalPath)
        assertTrue(restored.isPinned)
        assertFalse(restored.isViewOnly)
    }

    @Test
    fun invalidMembersJsonFallsBackToEmptyMembership() {
        val entity = CanvasEntity(
            id = "canvas-2",
            ownerId = "owner-2",
            name = "Broken Cache Entry",
            widthPx = 1024,
            heightPx = 768,
            membersJson = "{not valid json",
            shareCode = "WXYZ99",
            isViewOnly = true,
            isPinned = false,
            createdAt = 1,
            updatedAt = 2,
            thumbnailLocalPath = null
        )

        val restored = entity.toDomain()

        assertEquals("canvas-2", restored.id)
        assertTrue(restored.members.isEmpty())
        assertTrue(restored.isViewOnly)
    }
}