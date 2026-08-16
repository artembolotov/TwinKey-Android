package com.artembolotov.twinkey.ui.add

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.artembolotov.twinkey.R
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Порт AccountScanScreen.swift + ScanAccountView.swift.
 *
 * CameraX + ML Kit: сканирует QR-код otpauth://.
 * Кнопки внизу: "Select from Photos" и "Add Manually".
 * Cancel (крестик) в TopAppBar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onScanned: (String) -> Unit,
    onAddManually: () -> Unit,
    onCancel: () -> Unit
) {
    BackHandler(onBack = onCancel)

    val context = LocalContext.current
    val state = remember {
        QrScannerState(
            hasCameraPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> state.hasCameraPermission = granted }

    // Выбор изображения из галереи
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && !state.scanned) {
            val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
                ?: return@rememberLauncherForActivityResult
            val client = BarcodeScanning.getClient()
            client.process(image)
                .addOnSuccessListener { barcodes ->
                    barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                        ?.rawValue
                        ?.let { url ->
                            state.scanned = true
                            onScanned(url)
                        }
                }
                .addOnCompleteListener { client.close() }
        }
    }

    LaunchedEffect(Unit) {
        if (!state.hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.scan_qr_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            BottomAppBar(containerColor = Color.Black.copy(alpha = 0.7f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Text(stringResource(R.string.scan_select_from_photos), color = Color.White)
                    }
                    TextButton(onClick = onAddManually) {
                        Text(stringResource(R.string.scan_add_manually), color = Color.White)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.hasCameraPermission) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onQrCodeDetected = { url ->
                        if (!state.scanned) {
                            state.scanned = true
                            onScanned(url)
                        }
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.scan_no_camera_permission),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.scan_grant_permission))
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private class QrScannerState(hasCameraPermission: Boolean) {
    var hasCameraPermission by mutableStateOf(hasCameraPermission)
    var scanned by mutableStateOf(false)
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onQrCodeDetected: (String) -> Unit
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }

    // Камера привязывается к жизненному циклу Activity, а не экрана, поэтому уход
    // с экрана сам по себе её не отвязывает: ImageAnalysis продолжает слать кадры
    // в уже погашенный executor и закрытый ML Kit-клиент. Держим провайдер, чтобы
    // сделать unbind вручную, и флаг — на кадры, которые успели проскочить.
    val cameraProviderRef = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val released = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            released.set(true)
            runCatching { cameraProviderRef.getAndSet(null)?.unbindAll() }
            executor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                // get() бросает ExecutionException, если инициализация CameraX не
                // удалась (нет камеры, занята другим приложением). Слушатель крутится
                // на главном потоке, так что непойманное исключение — падение приложения.
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    if (released.get()) {
                        cameraProvider.unbindAll()
                        return@addListener
                    }
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(executor) { imageProxy ->
                                analyzeFrame(imageProxy, scanner, released, onQrCodeDetected)
                            }
                        }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                    )
                    cameraProviderRef.set(cameraProvider)
                    // Экран мог уйти из композиции, пока шла привязка.
                    if (released.get()) cameraProviderRef.getAndSet(null)?.unbindAll()
                } catch (e: Exception) {
                    Log.w("QrScannerScreen", "Camera init failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analyzeFrame(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    released: AtomicBoolean,
    onQrCodeDetected: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null || released.get()) {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    // Клиент могли закрыть между проверкой флага и вызовом — тогда process()
    // бросает IllegalStateException на потоке анализа, а он никем не перехвачен.
    val task = runCatching { scanner.process(image) }.getOrNull()
    if (task == null) {
        imageProxy.close()
        return
    }
    task
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                ?.rawValue
                ?.let { onQrCodeDetected(it) }
        }
        .addOnCompleteListener { imageProxy.close() }
}
