package com.my_app.art_collab.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.my_app.art_collab.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiApiClient {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val MODEL = "gemini-2.5-flash-image"

    suspend fun generateImage(prompt: String): Bitmap = withContext(Dispatchers.IO) {
        val url = URL("$BASE_URL/$MODEL:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val body = JSONObject().apply {
                put("contents", org.json.JSONArray().put(
                    JSONObject().put("parts", org.json.JSONArray().put(
                        JSONObject().put("text", prompt)
                    ))
                ))
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", org.json.JSONArray().put("TEXT").put("IMAGE"))
                })
            }

            connection.outputStream.use { it.write(body.toString().toByteArray()) }

            if (connection.responseCode != 200) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                throw Exception("API error ${connection.responseCode}: $errorBody")
            }

            val responseText = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(responseText)

            val parts = json
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("inlineData")) {
                    val inlineData = part.getJSONObject("inlineData")
                    val base64 = inlineData.getString("data")
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: throw Exception("Failed to decode image")
                }
            }

            throw Exception("No image in response")
        } finally {
            connection.disconnect()
        }
    }
}
