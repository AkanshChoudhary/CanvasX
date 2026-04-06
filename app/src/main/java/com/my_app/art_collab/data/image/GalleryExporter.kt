package com.my_app.art_collab.data.image
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
object GalleryExporter {
    private var ALBUM_RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/CanvasX"

    fun savePngLossless(context: Context, bitmap: Bitmap, baseDisplayName: String): Result<Unit>{
        if(bitmap.isRecycled){
            return Result.failure(IllegalStateException("Bitmap was recycled"))
        }
        val toWrite = if(bitmap.config == Bitmap.Config.HARDWARE){
            bitmap.copy(Bitmap.Config.ARGB_8888, false)?: return Result.failure(
                IllegalStateException("Could Not copy hardware bitmmap"))
        }else{
            bitmap
        }

        val resolver = context.contentResolver
        val safeBase = baseDisplayName
            .replace(Regex("""[<>:"/\\|?*]"""), "_")
            .trim()
            .ifBlank { "Canvas" }

        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val fileName = "${safeBase}_${stamp}.png"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, ALBUM_RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
            ?: return Result.failure(IllegalStateException("Could not create gallery entry"))

        return try{
            resolver.openOutputStream(uri)?.use { out->
                if(!toWrite.compress(Bitmap.CompressFormat.PNG,100,out)){
                    throw IllegalStateException("Could not write bitmap to gallery")
                }
            }?: throw IllegalStateException("Could Not open output stream")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING,0)
            resolver.update(uri,values, null,null)
            Result.success(Unit)
        }catch (e: Exception) {
            resolver.delete(uri, null, null)
            Result.failure(e)
        } finally {
            if (toWrite !== bitmap) {
                toWrite.recycle()
            }
        }
    }

}