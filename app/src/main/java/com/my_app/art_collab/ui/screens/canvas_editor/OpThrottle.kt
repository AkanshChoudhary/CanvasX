package com.my_app.art_collab.ui.screens.canvas_editor

import androidx.compose.runtime.snapshots.Snapshot
import com.my_app.art_collab.domain.model.LayerOp

class OpThrottle(private val intervalMs: Long = 50L) {
    private var lastSentAt = 0L
    private var pendingOp: Pair<LayerOp, Map<String, Any?>>? = null
    fun onOp(op: LayerOp,snapshot: Map<String, Any?>, send: (LayerOp,Map<String,Any?>)->Unit){
        pendingOp = op to snapshot
        val now = System.currentTimeMillis()
        if(now-lastSentAt>=intervalMs){
            flush(send)
        }
    }
    fun onFinalOp(op: LayerOp, snapshot: Map<String,Any?>, send: (LayerOp,Map<String,Any?>)->Unit){
        pendingOp = op to snapshot
        flush(send)
    }

    private fun flush(send: (LayerOp,Map<String,Any?>)->Unit){
        pendingOp?.let { (op,snapshot) ->
            send(op,snapshot)
            lastSentAt = System.currentTimeMillis()
            pendingOp = null
        }
    }
}