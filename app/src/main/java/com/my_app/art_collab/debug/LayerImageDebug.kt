package com.my_app.art_collab.debug

/**
 * Filter Logcat by tag: `LayerImageDebug`
 */
object LayerImageDebug {
    const val TAG = "LayerImageDebug"

    fun pathPreview(path: String?, maxLen: Int = 96): String {
        if (path == null) return "<null>"
        if (path.isEmpty()) return "<empty>"
        val t = path.trim()
        if (t.isEmpty()) return "<blank>"
        return if (t.length <= maxLen) t else t.take(maxLen) + "…(${t.length}ch)"
    }
}
