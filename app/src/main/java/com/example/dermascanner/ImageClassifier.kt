//package com.example.dermascanner
//
//import android.content.Context
//import android.graphics.Bitmap
//import org.tensorflow.lite.Interpreter
//import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//
//class UnetModel(context: Context) {
//
//    private val interpreter: Interpreter
//    private val imageSize = 256 // Debe coincidir con IMAGE_SIZE usado al entrenar
//
//    init {
//        val assetFileDescriptor = context.assets.openFd("unet_model.tflite")
//        val fileInputStream = assetFileDescriptor.createInputStream()
//        val model = fileInputStream.readBytes()
//        //interpreter = Interpreter(model)
//    }
//
//    fun predict(bitmap: Bitmap): Bitmap {
//        // Preprocesamiento
//        val resized = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true)
//        val byteBuffer = convertBitmapToByteBuffer(resized)
//
//        // Crear buffer de salida (ajustar según output_shape del modelo, ej: [1, 128, 128, 2])
//        val outputShape = intArrayOf(1, imageSize, imageSize, 2)
//        val outputBuffer = TensorBuffer.createFixedSize(outputShape, org.tensorflow.lite.DataType.FLOAT32)
//
//        // Ejecutar
//        interpreter.run(byteBuffer, outputBuffer.buffer.rewind())
//
//        // Aquí podrías aplicar post-procesamiento para extraer máscara o clasificación
//        // Por simplicidad, devolvemos el mismo bitmap (realmente deberías devolver una máscara)
//        return resized
//    }
//
//    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
//        val byteBuffer = ByteBuffer.allocateDirect(1 * imageSize * imageSize * 3 * 4)
//        byteBuffer.order(ByteOrder.nativeOrder())
//        val pixels = IntArray(imageSize * imageSize)
//        bitmap.getPixels(pixels, 0, imageSize, 0, 0, imageSize, imageSize)
//        for (pixel in pixels) {
//            val r = (pixel shr 16 and 0xFF) / 255.0f
//            val g = (pixel shr 8 and 0xFF) / 255.0f
//            val b = (pixel and 0xFF) / 255.0f
//            byteBuffer.putFloat(r)
//            byteBuffer.putFloat(g)
//            byteBuffer.putFloat(b)
//        }
//        return byteBuffer
//    }
//}
