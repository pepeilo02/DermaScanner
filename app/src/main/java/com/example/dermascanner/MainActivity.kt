package com.example.dermascanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import androidx.core.graphics.scale
import com.example.dermascanner.ml.Unet
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

data class PhotoEntry(
    val imagePath: String,
    val maskPath: String,
    val timestamp: Long
)


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppNavigation()
        }
    }
}

enum class Screen {
    HOME,
    CAMERA
}

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    val photoHistory = remember { mutableStateListOf<PhotoEntry>() }

    when (currentScreen) {
        Screen.HOME -> HomeScreen(
            onNavigateToCamera = { currentScreen = Screen.CAMERA },
            photoHistory = photoHistory
        )

        Screen.CAMERA -> CameraScreen(
            onNavigateBack = { currentScreen = Screen.HOME },
            onPhotoTaken = { entry -> photoHistory.add(entry) }
        )
    }
}

@Composable
fun HomeScreen(onNavigateToCamera: () -> Unit, photoHistory: List<PhotoEntry>) {


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = onNavigateToCamera,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Camera")
        }


        Spacer(modifier = Modifier.height(24.dp))

        Text("Historial de fotos:", style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(8.dp))

        if (photoHistory.isEmpty()) {
            Text("No hay fotos aún.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(
                rememberScrollState()
            )
            ) {
                photoHistory.reversed().forEach { entry ->
                    val imagePath = entry.imagePath
                    val bitmap = remember(imagePath) {
                        BitmapFactory.decodeFile(imagePath)
                    }
                    val maskPath = entry.maskPath
                    val maskBitmap = remember(maskPath) {
                        BitmapFactory.decodeFile(maskPath)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .scrollable(orientation = Orientation.Horizontal, state = rememberScrollState())
                            .clickable {  }
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(120.dp)
                                    .padding(end = 8.dp)
                                    .border(2.dp, Color.Black, RoundedCornerShape(8.dp)),

                                contentScale = ContentScale.Crop
                            )
                        }
                        if (maskBitmap != null) {
                            Image(
                                bitmap = maskBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(120.dp)
                                    .padding(end = 8.dp),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Column {
                            Text("📷 ${entry.maskPath}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "🕓 ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(entry.timestamp))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraScreen(onNavigateBack: () -> Unit,
                 onPhotoTaken: (PhotoEntry) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor: Executor = ContextCompat.getMainExecutor(context)

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(true) {
        if (!hasPermission) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasPermission) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()) {

                AndroidView(factory = { ctx ->
                    val previewView = PreviewView(ctx)

                    val preview = Preview.Builder().build()
                    imageCapture = ImageCapture.Builder().build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )

                    preview.surfaceProvider = previewView.surfaceProvider
                    previewView
                })

                val density = LocalDensity.current.density
                val framingBoxSizeDp = (256 / density).dp
                Box(
                    modifier = Modifier
                        .size(framingBoxSizeDp)
                        .align(Alignment.Center)
                        .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                )


            }

            Button(
                onClick = {
                    imageCapture?.let { capture ->
                        val timestamp = System.currentTimeMillis()
                        val fileName = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date(timestamp)) + ".jpg"

                        val photoFile = File(context.cacheDir, fileName)

                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                        capture.takePicture(
                            outputOptions,
                            executor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onError(exc: ImageCaptureException) {
                                    Log.e("Camera", "Photo capture failed: ${exc.message}", exc)
                                }

                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {

                                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                    if (bitmap == null) {
                                        Toast.makeText(context, "Error decoding image", Toast.LENGTH_SHORT).show()
                                        return
                                    }

                                    val rotatedBitmap = rotateBitmapIfRequired(bitmap, photoFile)

                                    val croppedBitmap = cropCenterSquare(rotatedBitmap, 256, 256)
                                    val byteBuffer = convertBitmapToByteBuffer(croppedBitmap)

                                    val model = Unet.newInstance(context)
                                    val input = TensorBuffer.createFixedSize(intArrayOf(1, 256, 256, 3), DataType.FLOAT32)
                                    input.loadBuffer(byteBuffer)

                                    val outputs = model.process(input)
                                    val outputTensor = outputs.outputFeature0AsTensorBuffer
                                    val outputArray = outputTensor.floatArray
                                    println(outputArray)
                                    model.close()

                                    val maskBitmap = convertOutputToMask(outputArray)

                                    val croppedImageBitmap = cropMaskIntoImage(maskBitmap, croppedBitmap)

                                    val maskFile = File(
                                        context.cacheDir,
                                        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date(timestamp)) + "_mask.png"
                                    )
                                    FileOutputStream(maskFile).use { out ->
                                        maskBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                                    }

                                    val imageFile= File(context.cacheDir, fileName)
                                    FileOutputStream(imageFile).use { out ->
                                        croppedImageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                                    }

                                    // 6. Actualizar historial (pasar entrada al callback)
                                    onPhotoTaken(
                                        PhotoEntry(
                                            imagePath = imageFile.absolutePath,
                                            maskPath = maskFile.absolutePath,
                                            timestamp = timestamp
                                        )
                                    )

                                    Toast.makeText(context, "Photo & mask saved!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Take Photo")
            }


            Button(
                onClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Back to Home")
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Camera permission is required.")
            Button(onClick = onNavigateBack) {
                Text("Back to Home")
            }
        }
    }
}

fun cropCenterSquare(bitmap: Bitmap, cropWidth: Int, cropHeight: Int): Bitmap {
    val x = (bitmap.width - cropWidth) / 2
    val y = (bitmap.height - cropHeight) / 2
    return Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
}