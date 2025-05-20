package com.example.dermascanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
import imageClassifier

data class PhotoEntry(
    val imagePath: String,
    val croppedImagePath: String,
    val maskPath: String,
    val prediction: String,
    val confidence: Float,
    val timestamp: Long
)

val CROPPING_BOX_SIZE = 130.dp

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
                            Text("📷 ${entry.prediction}", style = MaterialTheme.typography.bodyMedium)
                            Text("📊 ${entry.confidence}", style = MaterialTheme.typography.bodyMedium)
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

    val previewViewRef = remember { mutableStateOf<PreviewView?>(null) }

    val croppingBoxCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
    if (hasPermission) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()) {



                AndroidView(factory = { ctx ->
                    val previewView = PreviewView(ctx)

                    previewViewRef.value = previewView


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



                Box(
                    modifier = Modifier
                        .size(CROPPING_BOX_SIZE)
                        .align(Alignment.Center)
                        .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                        .onGloballyPositioned { coordinates -> croppingBoxCoords.value = coordinates }
                )


            }
                GalleryPickerButton(LocalContext.current, onPhotoTaken = onPhotoTaken)
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

                                        val density = context.resources.displayMetrics.density
                                        val cropSizePx = (CROPPING_BOX_SIZE.value * density).toInt()
                                        val rotatedBitmap = rotateBitmapIfRequired(bitmap, photoFile)

                                        val croppedBitmap = cropBitmapFromPreviewBox(
                                            rotatedBitmap,
                                            previewViewRef.value!!,
                                            croppingBoxCoords.value!!,
                                            outputSize = 256
                                        )


                                        val result = imageClassifier(croppedBitmap, photoFile, context, fileName, timestamp)

                                        onPhotoTaken(
                                            result
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
@Composable
fun GalleryPickerButton(context: Context, onPhotoTaken: (PhotoEntry) -> Unit) {
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)


            if (bitmap != null) {
                val timestamp = System.currentTimeMillis()
                val fileName = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date(timestamp)) + ".jpg"
                val photoFile = File(context.cacheDir, fileName)
                val resizedBitmap = bitmap.scale(256,256)

                FileOutputStream(photoFile).use { out ->
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }

                val result=imageClassifier(resizedBitmap, photoFile, context, fileName, timestamp)

                onPhotoTaken(result)

                Toast.makeText(context, "Photo & mask saved!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d("PhotoPicker", "No media selected")
        }
    }

    Button(
        onClick = {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("Open Gallery")
    }
}