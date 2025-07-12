package com.example.dermascanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import androidx.core.graphics.scale
import androidx.room.Room
import java.io.FileOutputStream
import imageClassifier


val CROPPING_BOX_SIZE = 130.dp

class DatabaseClient(private val context: Context) {
    val appDatabase: AppDatabase by lazy {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "photoEntry"
        ).allowMainThreadQueries()
        .build()
    }
}
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

    val context = LocalContext.current
    val db = remember { DatabaseClient(context).appDatabase }
    val photoDao = remember {db.photoEntryDao()}

    when (currentScreen) {
        Screen.HOME -> HomeScreen(
            onNavigateToCamera = { currentScreen = Screen.CAMERA },
            photoDao = photoDao
        )

        Screen.CAMERA -> CameraScreen(
            onNavigateBack = { currentScreen = Screen.HOME },
            onPhotoTaken = { entry -> photoDao.insert(entry) }
        )
    }
}

@Composable
fun HomeScreen(onNavigateToCamera: () -> Unit, photoDao: PhotoEntryDao) {
    val photoHistory = photoDao.getAll()

    var selectedEntry by remember { mutableStateOf<PhotoEntry?>(null) }

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
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                photoHistory.reversed().forEach { entry ->
                    val bitmap = remember(entry.imagePath) { BitmapFactory.decodeFile(entry.croppedImagePath) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .scrollable(orientation = Orientation.Horizontal, state = rememberScrollState())
                            .clickable { selectedEntry = entry }
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
                        Column {
                            Text("📷 ${entry.prediction}", style = MaterialTheme.typography.bodyMedium)
                            Text("📊 ${"%.2f".format(entry.confidence * 100)}%", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "🕓 ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(entry.timestamp))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Column{
                            Button(onClick = {
                                photoDao.delete(entry)
                            }) {
                                Text("Eliminar")
                            }
                        }
                    }
                }
            }
        }
    }
    selectedEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { selectedEntry = null },
            title = { Text(text = "Detalles de la foto") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val originalBitmap = BitmapFactory.decodeFile(entry.imagePath)
                    val croppedBitmap = BitmapFactory.decodeFile(entry.croppedImagePath)

                    if (originalBitmap != null) {
                        Text("Foto Original")
                        Image(
                            bitmap = originalBitmap.asImageBitmap(),
                            contentDescription = "Foto original",
                            modifier = Modifier
                                .size(200.dp)
                                .padding(8.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (croppedBitmap != null) {
                        Text("Foto Recortada")
                        Image(
                            bitmap = croppedBitmap.asImageBitmap(),
                            contentDescription = "Foto recortada",
                            modifier = Modifier
                                .size(200.dp)
                                .padding(8.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Predicción: ${entry.prediction}", style = MaterialTheme.typography.bodyLarge)
                    Text("Confianza: ${"%.2f".format(entry.confidence * 100)}%", style = MaterialTheme.typography.bodyMedium)
                    if (entry.prediction=="Maligno" && entry.confidence>0.75){
                        Text(color=Color.Red,text="Deberia consultar con su médico esta lesión")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedEntry = null }) {
                    Text("Cerrar")
                }
            }
        )
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

    // Estados para controlar la carga y el diálogo de resultado
    var isLoading by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<PhotoEntry?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
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

                GalleryPickerButton(context, onPhotoTaken = { entry ->
                    analysisResult = entry
                    showResultDialog = true
                    onPhotoTaken(entry)
                })

                Button(
                    onClick = {
                        isLoading = true // Muestra el spinner
                        imageCapture?.let { capture ->
                            val timestamp = System.currentTimeMillis()
                            val fileName = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date(timestamp)) + ".jpg"
                            val photoFile = File(context.filesDir, fileName)
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                            capture.takePicture(
                                outputOptions,
                                executor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onError(exc: ImageCaptureException) {
                                        Log.e("Camera", "Photo capture failed: ${exc.message}", exc)
                                        isLoading = false // Oculta el spinner en caso de error
                                    }

                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                        if (bitmap == null) {
                                            Toast.makeText(context, "Error decoding image", Toast.LENGTH_SHORT).show()
                                            isLoading = false
                                            return
                                        }

                                        val rotatedBitmap = rotateBitmapIfRequired(bitmap, photoFile)
                                        val croppedBitmap = cropBitmapFromPreviewBox(
                                            rotatedBitmap,
                                            previewViewRef.value!!,
                                            croppingBoxCoords.value!!,
                                            outputSize = 256
                                        )

                                        val croppedFile = File(context.filesDir, fileName)
                                        FileOutputStream(croppedFile).use { out ->
                                            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                                        }

                                        val result = imageClassifier(croppedBitmap, photoFile, context, fileName, timestamp)
                                        onPhotoTaken(result)

                                        // Actualiza estados para mostrar el diálogo
                                        analysisResult = result
                                        isLoading = false // Oculta el spinner
                                        showResultDialog = true // Muestra el diálogo
                                    }
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Hacer foto")
                }

                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Volver al historial")
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

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false, onClick = {}),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        if (showResultDialog) {
            analysisResult?.let { entry ->
                AlertDialog(
                    onDismissRequest = { showResultDialog = false },
                    title = { Text(text = "Detalles de la foto") },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val originalBitmap = BitmapFactory.decodeFile(entry.imagePath)
                            val croppedBitmap = BitmapFactory.decodeFile(entry.croppedImagePath)

                            if (originalBitmap != null) {
                                Text("Foto Original")
                                Image(
                                    bitmap = originalBitmap.asImageBitmap(),
                                    contentDescription = "Foto original",
                                    modifier = Modifier
                                        .size(200.dp)
                                        .padding(8.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            if (croppedBitmap != null) {
                                Text("Foto Recortada")
                                Image(
                                    bitmap = croppedBitmap.asImageBitmap(),
                                    contentDescription = "Foto recortada",
                                    modifier = Modifier
                                        .size(200.dp)
                                        .padding(8.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Predicción: ${entry.prediction}", style = MaterialTheme.typography.bodyLarge)
                            Text("Confianza: ${"%.2f".format(entry.confidence * 100)}%", style = MaterialTheme.typography.bodyMedium)
                            if (entry.prediction == "Maligno" && entry.confidence > 0.75) {
                                Text(color = Color.Red, text = "Deberia consultar con su médico esta lesión")
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showResultDialog = false }) {
                            Text("Cerrar")
                        }
                    }
                )
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
                val photoFile = File(context.filesDir, fileName)
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
        Text("Abrir Galería")
    }
}