import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalContext
import com.example.dermascanner.PhotoEntry
import com.example.dermascanner.convertBitmapToByteBuffer
import com.example.dermascanner.convertBitmapToByteBufferUInt
import com.example.dermascanner.convertOutputToMask
import com.example.dermascanner.cropMaskIntoImage
import com.example.dermascanner.ml.Classifier1
import com.example.dermascanner.ml.Unet
import com.example.dermascanner.rotateBitmapIfRequired
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun imageClassifier(bitmap: Bitmap, photoFile: File, context: Context, fileName: String, timestamp: Long) : PhotoEntry {

    val byteBuffer = convertBitmapToByteBuffer(bitmap)

    val model = Unet.newInstance(context)
    val input = TensorBuffer.createFixedSize(intArrayOf(1, 256, 256, 3), DataType.FLOAT32)
    input.loadBuffer(byteBuffer)

    val outputs = model.process(input)
    val outputTensor = outputs.outputFeature0AsTensorBuffer
    val outputArray = outputTensor.floatArray
    println(outputArray)
    model.close()

    val maskBitmap = convertOutputToMask(outputArray)

    val croppedImageBitmap = cropMaskIntoImage(maskBitmap, bitmap)

    val byteCroppedBuffer = convertBitmapToByteBufferUInt(croppedImageBitmap)

    val classifier = Classifier1.newInstance(context)
    val input2 = TensorBuffer.createFixedSize(intArrayOf(1, 256, 256, 3), DataType.FLOAT32)
    input2.loadBuffer(byteCroppedBuffer)

    val outputs2 = classifier.process(input2)
    val outputTensor2 = outputs2.getOutputFeature0AsTensorBuffer()
    val outputArray2 = outputTensor2.floatArray
    classifier.close()

    val predictionIndex = outputArray2.indices.maxByOrNull { outputArray2[it] } ?: 0

    val prediction = if (predictionIndex == 0) "Benigno" else "Maligno"

    val probability = outputArray2[predictionIndex]


    val maskFile = File(
        context.cacheDir,
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date(timestamp)) + "_mask.png"
    )
    FileOutputStream(maskFile).use { out ->
        maskBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
    }

    val croppedImageFile= File(context.cacheDir, fileName)
    FileOutputStream(croppedImageFile).use { out ->
        croppedImageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
    }

    return PhotoEntry(
        imagePath = photoFile.absolutePath,
        croppedImagePath = croppedImageFile.absolutePath,
        maskPath = maskFile.absolutePath,
        prediction = prediction,
        confidence = probability,
        timestamp = timestamp
    )
}