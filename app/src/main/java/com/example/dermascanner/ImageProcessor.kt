package com.example.dermascanner

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.core.graphics.get
import androidx.core.graphics.scale

fun rotateBitmapIfRequired(bitmap: Bitmap, imageFile: File): Bitmap {
    val exif = ExifInterface(imageFile.absolutePath)
    val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    }

    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

fun convertOutputToMask(outputArray: FloatArray): Bitmap {
    val width = 256
    val height = 256
    val mask = createBitmap(width, height)

    for (y in 0 until height) {
        for (x in 0 until width) {
            val offset = (y * width + x) * 2
            val class0 = outputArray[offset]
            val class1 = outputArray[offset + 1]
            val isForeground = class1 > class0
            val color = if (isForeground) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            mask[x, y] = color
        }
    }
    return mask
}

fun cropMaskIntoImage(mask: Bitmap, image: Bitmap): Bitmap {
    val width = mask.width
    val height = mask.height

    val resizedImage = image.scale(width, height, false)
    val result = createBitmap(width, height)

    for (y in 0 until height) {
        for (x in 0 until width) {
            val maskPixel = mask[x, y]
            val maskValue = Color.red(maskPixel)

            if (maskValue > 128) {
                val originalPixel = resizedImage[x, y]
                result[x, y] = originalPixel
            } else {
                result[x, y] = Color.BLACK
            }
        }
    }

    return result
}




fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
    val byteBuffer = ByteBuffer.allocateDirect(4 * 256 * 256 * 3)
    byteBuffer.order(ByteOrder.nativeOrder())

    val intValues = IntArray(256 * 256)
    bitmap.getPixels(intValues, 0, 256, 0, 0, 256, 256)
    for (pixelValue in intValues) {
        val r = (pixelValue shr 16 and 0xFF) / 255.0f
        val g = (pixelValue shr 8 and 0xFF) / 255.0f
        val b = (pixelValue and 0xFF) / 255.0f
        byteBuffer.putFloat(r)
        byteBuffer.putFloat(g)
        byteBuffer.putFloat(b)
    }
    byteBuffer.rewind()
    return byteBuffer
}

fun convertBitmapToByteBufferUInt(bitmap: Bitmap): ByteBuffer{
    val byteBuffer = ByteBuffer.allocateDirect(4 * 256 * 256 * 3)
    byteBuffer.order(ByteOrder.nativeOrder())

    val intValues = IntArray(256 * 256)
    bitmap.getPixels(intValues, 0, 256, 0, 0, 256, 256)
    for (pixelValue in intValues) {
        val r = (pixelValue shr 16 and 0xFF).toFloat()
        val g = (pixelValue shr 8 and 0xFF).toFloat()
        val b = (pixelValue and 0xFF).toFloat()
        byteBuffer.putFloat(r)
        byteBuffer.putFloat(g)
        byteBuffer.putFloat(b)
    }
    byteBuffer.rewind()
    return byteBuffer
}

fun cropBitmapFromPreviewBox(
    bitmap: Bitmap,
    previewView: PreviewView,
    croppingBoxCoords: LayoutCoordinates,
    outputSize: Int = 256
): Bitmap {

        val viewLocation = IntArray(2)
        previewView.getLocationOnScreen(viewLocation)
        val previewLeft = viewLocation[0]
        val previewTop = viewLocation[1]

        val previewWidth = previewView.width
        val previewHeight = previewView.height

        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height

        val boxRect = croppingBoxCoords.boundsInWindow()

        val scaleX = bitmapWidth.toFloat() / previewWidth
        val scaleY = bitmapHeight.toFloat() / previewHeight

        val cropLeft = ((boxRect.left - previewLeft) * scaleX).toInt()
        val cropTop = ((boxRect.top - previewTop) * scaleY).toInt()
        val cropWidth = (boxRect.width * scaleX).toInt()
        val cropHeight = (boxRect.height * scaleY).toInt()

        val safeLeft = cropLeft.coerceIn(0, bitmapWidth - 1)
        val safeTop = cropTop.coerceIn(0, bitmapHeight - 1)
        val safeWidth = cropWidth.coerceAtMost(bitmapWidth - safeLeft)
        val safeHeight = cropHeight.coerceAtMost(bitmapHeight - safeTop)

        val croppedBitmap =
            Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)

        return croppedBitmap.scale(outputSize, outputSize)

}
