package com.example.object_detection_poc.feature.detection

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

@Composable
fun DetectionScreen(
    viewModel: DetectionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }

    // Check initial permission and request if needed
    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = { CustomAppBar() }
    ) { paddingValues ->
        Column(
            verticalArrangement = Arrangement.SpaceAround,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            CameraPreview(
                hasCameraPermission = uiState.hasCameraPermission,
                onTextRecognized = viewModel::onTextRecognized
            )
            Instructions()
            Status(
                framerate = uiState.fps,
                blocks = uiState.textBlocks
            )
            RecognizedText(text = uiState.recognizedText)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomAppBar() {
    TopAppBar(
        title = { Text("Reconhecimento de Texto") }
    )
}

@Composable
fun CameraPreview(
    hasCameraPermission: Boolean,
    onTextRecognized: (text: String, blockCount: Int) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    val shape = RoundedCornerShape(10.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(
                width = ((358f / 390f) * screenWidth).dp,
                height = ((318f / 844f) * screenHeight).dp,
            )
            .clip(shape)
            .border(1.dp, Color(0xFFBFCABA), shape)
    ) {
        if (hasCameraPermission) {
            CameraViewFinder(onTextRecognized = onTextRecognized)
        } else {
            Text(
                text = "Permissão de câmera necessária",
                color = Color.Gray
            )
        }
    }
}

@Composable
fun CameraViewFinder(onTextRecognized: (text: String, blockCount: Int) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnTextRecognized by rememberUpdatedState(onTextRecognized)

    val previewView = remember { PreviewView(context) }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false

        cameraProviderFuture.addListener({
            if (disposed) return@addListener
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            // Cada frame da câmera é enviado ao ML Kit Text Recognition (OCR)
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        mainExecutor,
                        MlKitAnalyzer(
                            listOf(recognizer),
                            ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL,
                            mainExecutor
                        ) { result ->
                            val error = result.getThrowable(recognizer)
                            val text = result.getValue(recognizer)
                            if (error != null) {
                                Log.e("CameraPreview", "Falha no reconhecimento de texto", error)
                            } else if (text != null) {
                                currentOnTextRecognized(text.text, text.textBlocks.size)
                            }
                        }
                    )
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("CameraPreview", "Falha ao iniciar câmera", e)
            }
        }, mainExecutor)

        onDispose {
            disposed = true
            if (cameraProviderFuture.isDone) {
                cameraProviderFuture.get().unbindAll()
            }
            recognizer.close()
        }
    }
}

@Composable
fun Instructions() {
    Text("Aponte a câmera para um texto")
}

@Composable
fun Status(framerate: Int, blocks: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("FPS: $framerate")
        Text("Blocos de texto: $blocks")
        Text("Modelo: ML Kit Text Recognition (Latin)")
    }
}

@Composable
fun RecognizedText(text: String) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .size(
                width = ((358f / 390f) * screenWidth).dp,
                height = ((180f / 844f) * screenHeight).dp,
            )
            .clip(shape)
            .border(1.dp, Color(0xFFBFCABA), shape)
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = text.ifBlank { "Nenhum texto detectado" },
            color = if (text.isBlank()) Color.Gray else Color.Unspecified
        )
    }
}