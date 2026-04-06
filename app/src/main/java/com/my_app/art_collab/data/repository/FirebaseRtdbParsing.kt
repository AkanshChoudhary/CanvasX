package com.my_app.art_collab.data.repository

/**
 * Firebase RTDB stores numbers as Long or Double. [com.google.firebase.database.DataSnapshot.getValue]
 * with Int::class often returns null for integer fields. Op payload maps use JVM boxed types.
 */
internal fun Any?.asFirebaseInt(): Int? = when (this) {
    is Int -> this
    is Long -> toInt()
    is Double -> toInt()
    is Float -> toInt()
    is String -> toIntOrNull()
    else -> null
}

internal fun Any?.asFirebaseFloat(): Float? = when (this) {
    is Float -> this
    is Double -> toFloat()
    is Int -> toFloat()
    is Long -> toFloat()
    is String -> toFloatOrNull()
    else -> null
}
