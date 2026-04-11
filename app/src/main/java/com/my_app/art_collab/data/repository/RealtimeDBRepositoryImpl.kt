package com.my_app.art_collab.data.repository

import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.my_app.art_collab.domain.model.Effect
import com.my_app.art_collab.domain.model.Layer
import com.my_app.art_collab.domain.model.LayerOp
import com.my_app.art_collab.domain.model.LayerTransform
import com.my_app.art_collab.domain.model.LayerType
import com.my_app.art_collab.domain.model.TextLayerContent
import com.my_app.art_collab.debug.LayerImageDebug
import com.my_app.art_collab.domain.repository.RealtimeDBRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class RealtimeDBRepositoryImpl @Inject constructor() : RealtimeDBRepository {
    private val database = FirebaseDatabase.getInstance()

    override suspend fun pushOpWithSnapshot(canvasId: String, op: LayerOp, snapshotUpdate: Map<String, Any?>) {
        val opPath = "canvases/$canvasId/ops/${op.id}"
        val opData = mapOf(
            "userId" to op.userId,
            "layerId" to op.layerId,
            "type" to op.type,
            "payload" to op.payload,
            "timestamp" to op.timestamp
        )

        val updates = mutableMapOf<String, Any?>()
        updates[opPath] = opData
        updates.putAll(snapshotUpdate)

        try {
            database.reference.updateChildren(updates).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun buildLayerDataMap(canvasId: String, layer: Layer, blobUrl: String?): Map<String, Any?> {
        // Prefer explicit upload URL; otherwise keep the layer's current path so ops (effects,
        // transform, etc.) never wipe Firebase blobUrl when _layerBlobUrls[layerId] is empty.
        val resolvedBlob = when {
            !blobUrl.isNullOrBlank() -> blobUrl
            !layer.sourceBitmapPath.isNullOrBlank() -> layer.sourceBitmapPath!!
            else -> ""
        }
        if (layer.type == LayerType.IMAGE || layer.type == LayerType.AI_GENERATED) {
            if (resolvedBlob.isBlank()) {
                Log.w(
                    LayerImageDebug.TAG,
                    "buildLayerDataMap: image layer ${layer.id} has no blobUrl or sourceBitmapPath to persist"
                )
            }
        }
        val layerData = mutableMapOf<String, Any>(
            "order" to layer.zIndex,
            "type" to layer.type.name.lowercase(),
            "blobUrl" to resolvedBlob,
            "opacity" to layer.opacity.toDouble(),
            "blendMode" to layer.blendMode.name.lowercase(),
            "name" to layer.name,
            "ownerId" to layer.ownerId,
            "solidColor" to (layer.solidColor ?: 0),
            "transform" to mapOf(
                "x" to layer.transform.translateX.toDouble(),
                "y" to layer.transform.translateY.toDouble(),
                "scaleX" to layer.transform.scaleX.toDouble(),
                "scaleY" to layer.transform.scaleY.toDouble()
            ),
            "updatedAt" to layer.updatedAt
        )

        layer.textContent?.let { tc ->
            layerData["textContent"] = mapOf(
                "text" to tc.text,
                "fontFamily" to tc.fontFamily,
                "fontSize" to tc.fontSize.toDouble(),
                "color" to tc.color,
                "isBold" to tc.isBold,
                "isItalic" to tc.isItalic,
                "isUnderline" to tc.isUnderline
            )
        }

        if (layer.effectChain.isNotEmpty()) {
            layerData["effects"] = layer.effectChain.associate { effect ->
                effect.id to effectToMap(effect)
            }
        }

        return mapOf("canvases/$canvasId/layers/${layer.id}" to layerData)
    }

    override suspend fun fetchLayers(canvasId: String): List<Layer> {
        return try {
            val snapshot = database.getReference("canvases/$canvasId/layers").get().await()
            val list = parseLayers(snapshot, canvasId)
            Log.d(LayerImageDebug.TAG, "fetchLayers: canvasId=$canvasId count=${list.size}")
            list
        } catch (e: Exception) {
            Log.e(LayerImageDebug.TAG, "fetchLayers failed canvasId=$canvasId", e)
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun deleteCanvasSubtree(canvasId: String) {
        database.getReference("canvases/$canvasId").removeValue().await()
    }

    override fun observeOps(canvasId: String, since: Long): Flow<LayerOp> = callbackFlow {
        val ref = database.getReference("canvases/$canvasId/ops")
            .orderByChild("timestamp")
            .startAt(since.toDouble())

        val listener = ref.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val op = parseOp(snapshot) ?: return
                trySend(op)
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseOp(snapshot: DataSnapshot): LayerOp? {
        val id = snapshot.key ?: return null
        val userId = snapshot.child("userId").getValue(String::class.java) ?: return null
        val layerId = snapshot.child("layerId").getValue(String::class.java) ?: ""
        val type = snapshot.child("type").getValue(String::class.java) ?: return null
        val payload = snapshot.child("payload").value as? Map<String, Any> ?: emptyMap()
        val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
        return LayerOp(id = id, userId = userId, layerId = layerId, type = type, payload = payload, timestamp = timestamp)
    }

    private fun parseLayers(snapshot: DataSnapshot, canvasId: String): List<Layer> {
        if (!snapshot.exists()) return emptyList()
        return snapshot.children.mapNotNull { child ->
            val layerId = child.key ?: return@mapNotNull null
            val typeStr = child.child("type").getValue(String::class.java) ?: "image"
            val layerType = parseLayerType(typeStr)
            val transformSnap = child.child("transform")
            val textSnap = child.child("textContent")

            val textContent = if (textSnap.exists()) {
                TextLayerContent(
                    text = textSnap.child("text").getValue(String::class.java) ?: "",
                    fontFamily = textSnap.child("fontFamily").getValue(String::class.java) ?: "sans-serif",
                    fontSize = textSnap.child("fontSize").value.asFirebaseFloat() ?: 12f,
                    color = textSnap.child("color").value.asFirebaseInt() ?: 0xFF000000.toInt(),
                    isBold = textSnap.child("isBold").getValue(Boolean::class.java) ?: false,
                    isItalic = textSnap.child("isItalic").getValue(Boolean::class.java) ?: false,
                    isUnderline = textSnap.child("isUnderline").getValue(Boolean::class.java) ?: false
                )
            } else null

            val effectsSnap = child.child("effects")
            val rawEffectChildren = if (effectsSnap.exists()) effectsSnap.children.toList() else emptyList()
            val effectChain = rawEffectChildren.mapNotNull { parseEffect(it) }
            if (rawEffectChildren.size != effectChain.size) {
                Log.w(
                    LayerImageDebug.TAG,
                    "parseLayers: ${rawEffectChildren.size - effectChain.size} effect(s) failed to parse for layer=$layerId " +
                        "(keys=${rawEffectChildren.mapNotNull { it.key }})"
                )
            }

            val blobFromDb = child.child("blobUrl").getValue(String::class.java)
            if (layerType == LayerType.IMAGE || layerType == LayerType.AI_GENERATED) {
                Log.d(
                    LayerImageDebug.TAG,
                    "parseLayers: layer=$layerType id=$layerId blobUrl=${LayerImageDebug.pathPreview(blobFromDb)} " +
                        "effectsParsed=${effectChain.size}/${rawEffectChildren.size} z=${child.child("order").value.asFirebaseInt() ?: 0}"
                )
            }

            Layer(
                id = layerId,
                canvasId = canvasId,
                ownerId = child.child("ownerId").getValue(String::class.java) ?: "",
                name = child.child("name").getValue(String::class.java) ?: "Layer",
                type = layerType,
                sourceBitmapPath = blobFromDb,
                zIndex = child.child("order").value.asFirebaseInt() ?: 0,
                opacity = child.child("opacity").value.asFirebaseFloat() ?: 1f,
                blendMode = parseBlendMode(
                    child.child("blendMode").getValue(String::class.java) ?: "normal"
                ),
                solidColor = if (layerType == LayerType.SOLID_COLOR) {
                    child.child("solidColor").value.asFirebaseInt()
                } else {
                    null
                },
                textContent = textContent,
                effectChain = effectChain,
                transform = LayerTransform(
                    translateX = transformSnap.child("x").value.asFirebaseFloat() ?: 0f,
                    translateY = transformSnap.child("y").value.asFirebaseFloat() ?: 0f,
                    scaleX = transformSnap.child("scaleX").value.asFirebaseFloat() ?: 1f,
                    scaleY = transformSnap.child("scaleY").value.asFirebaseFloat() ?: 1f
                ),
                updatedAt = child.child("updatedAt").getValue(Long::class.java) ?: 0L
            )
        }.sortedBy { it.zIndex }
    }

    private fun parseLayerType(type: String): LayerType = when (type) {
        "image" -> LayerType.IMAGE
        "solid_color" -> LayerType.SOLID_COLOR
        "text" -> LayerType.TEXT
        "ai_generated" -> LayerType.AI_GENERATED
        else -> LayerType.IMAGE
    }

    private fun parseBlendMode(mode: String): com.my_app.art_collab.domain.model.BlendMode = when (mode) {
        "normal" -> com.my_app.art_collab.domain.model.BlendMode.NORMAL
        "multiply" -> com.my_app.art_collab.domain.model.BlendMode.MULTIPLY
        "screen" -> com.my_app.art_collab.domain.model.BlendMode.SCREEN
        "overlay" -> com.my_app.art_collab.domain.model.BlendMode.OVERLAY
        "soft_light" -> com.my_app.art_collab.domain.model.BlendMode.SOFT_LIGHT
        else -> com.my_app.art_collab.domain.model.BlendMode.NORMAL
    }

    private fun effectToMap(effect: Effect): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "isEnabled" to effect.isEnabled
        )
        when (effect) {
            is Effect.BrightnessContrast -> {
                map["type"] = "brightness_contrast"
                map["brightness"] = effect.brightness.toDouble()
                map["contrast"] = effect.contrast.toDouble()
            }
            is Effect.Exposure -> {
                map["type"] = "exposure"
                map["stops"] = effect.stops.toDouble()
            }
            is Effect.GaussianBlur -> {
                map["type"] = "gaussian_blur"
                map["radius"] = effect.radius.toDouble()
            }
            is Effect.Sharpen -> {
                map["type"] = "sharpen"
                map["amount"] = effect.amount.toDouble()
            }
            is Effect.Vignette -> {
                map["type"] = "vignette"
                map["intensity"] = effect.intensity.toDouble()
                map["feather"] = effect.feather.toDouble()
            }
            is Effect.Saturation -> {
                map["type"] = "saturation"
                map["amount"] = effect.amount.toDouble()
            }
            is Effect.ColorTemperature -> {
                map["type"] = "color_temperature"
                map["temperature"] = effect.temperature.toDouble()
                map["tint"] = effect.tint.toDouble()
            }
            is Effect.Grain -> {
                map["type"] = "grain"
                map["amount"] = effect.amount.toDouble()
                map["size"] = effect.size.toDouble()
            }
            is Effect.Pixelate -> {
                map["type"] = "pixelate"
                map["blockSize"] = effect.blockSize.toDouble()
            }
        }
        return map
    }

    private fun parseEffect(snap: DataSnapshot): Effect? {
        val effectId = snap.key ?: return null
        val type = snap.child("type").getValue(String::class.java) ?: return null
        val isEnabled = snap.child("isEnabled").getValue(Boolean::class.java) ?: true

        return when (type) {
            "brightness_contrast" -> Effect.BrightnessContrast(
                id = effectId,
                isEnabled = isEnabled,
                brightness = snap.child("brightness").getValue(Double::class.java)?.toFloat() ?: 0f,
                contrast = snap.child("contrast").getValue(Double::class.java)?.toFloat() ?: 0f
            )
            "exposure" -> Effect.Exposure(
                id = effectId,
                isEnabled = isEnabled,
                stops = snap.child("stops").getValue(Double::class.java)?.toFloat() ?: 0f
            )
            "gaussian_blur" -> Effect.GaussianBlur(
                id = effectId,
                isEnabled = isEnabled,
                radius = snap.child("radius").getValue(Double::class.java)?.toFloat() ?: 5f
            )
            "sharpen" -> Effect.Sharpen(
                id = effectId,
                isEnabled = isEnabled,
                amount = snap.child("amount").getValue(Double::class.java)?.toFloat() ?: 0.5f
            )
            "vignette" -> Effect.Vignette(
                id = effectId,
                isEnabled = isEnabled,
                intensity = snap.child("intensity").getValue(Double::class.java)?.toFloat() ?: 0.5f,
                feather = snap.child("feather").getValue(Double::class.java)?.toFloat() ?: 0.5f
            )
            "saturation" -> Effect.Saturation(
                id = effectId,
                isEnabled = isEnabled,
                amount = snap.child("amount").getValue(Double::class.java)?.toFloat() ?: 0f
            )
            "color_temperature" -> Effect.ColorTemperature(
                id = effectId,
                isEnabled = isEnabled,
                temperature = snap.child("temperature").getValue(Double::class.java)?.toFloat() ?: 0f,
                tint = snap.child("tint").getValue(Double::class.java)?.toFloat() ?: 0f
            )
            "grain" -> Effect.Grain(
                id = effectId,
                isEnabled = isEnabled,
                amount = snap.child("amount").getValue(Double::class.java)?.toFloat() ?: 0.3f,
                size = snap.child("size").getValue(Double::class.java)?.toFloat() ?: 1f
            )
            "pixelate" -> Effect.Pixelate(
                id = effectId,
                isEnabled = isEnabled,
                blockSize = snap.child("blockSize").getValue(Double::class.java)?.toFloat() ?: 2f
            )
            else -> {
                Log.w(LayerImageDebug.TAG, "parseEffect: unknown type='$type' effectId=$effectId")
                null
            }
        }
    }
}