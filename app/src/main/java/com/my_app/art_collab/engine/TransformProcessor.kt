package com.my_app.art_collab.engine

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.Matrix
import com.my_app.art_collab.domain.model.LayerTransform
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.createBitmap

@Singleton
class TransformProcessor @Inject constructor() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    fun apply(
        layerBitmap: Bitmap,
        transform: LayerTransform,
        canvasWidth: Int,
        canvasHeight: Int
    ): Bitmap {
        val output = createBitmap(canvasWidth, canvasHeight)
        val canvas = Canvas(output)
        val matrix = Matrix()
        val canvasCenterX = canvasWidth / 2f
        val canvasCenterY = canvasHeight / 2f
        val layerCenterX = layerBitmap.width / 2f
        val layerCenterY = layerBitmap.height / 2f
        matrix.postScale(transform.scaleX, transform.scaleY, layerCenterX, layerCenterY)
        matrix.postTranslate(
            canvasCenterX - layerCenterX + transform.translateX,
            canvasCenterY - layerCenterY + transform.translateY
        )
        canvas.drawBitmap(layerBitmap, matrix, paint)
        return output
    }
}