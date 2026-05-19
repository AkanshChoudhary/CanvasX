package com.my_app.art_collab.debug

import android.util.Log
import com.my_app.art_collab.BuildConfig

object LayerImageDebug {
    const val TAG = "LayerImageDebug"

    fun pathPreview(path: String?, maxLen: Int = 96): String {
        if (path == null) return "<null>"
        if (path.isEmpty()) return "<empty>"
        val t = path.trim()
        if (t.isEmpty()) return "<blank>"
        return if (t.length <= maxLen) t else t.take(maxLen) + "…(${t.length}ch)"
    }

    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    fun w(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.w(tag, msg)
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.e(tag, msg, throwable) else Log.e(tag, msg)
        }
    }
}
