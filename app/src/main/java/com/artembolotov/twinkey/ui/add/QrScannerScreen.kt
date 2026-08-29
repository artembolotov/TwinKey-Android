package com.artembolotov.twinkey.ui.add

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.exifinterface.media.ExifInterface
import com.artembolotov.twinkey.R
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onCancel: () -> Unit,
    onNoQrCodeFound: () -> Unit,
    onScanError: () -> Unit
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

    val scope = rememberCoroutineScope()

    // Picking an image from the gallery. A failure is not a silent no-op: as on iOS,
    // dismiss the scanner and show a message over the accounts list. The split between
    // the two messages mirrors iOS as well: an unreadable image and an image without a
    // QR code are badOutput ("No QR code found in image"), while a failure of the
    // recognizer itself is unknown ("Scan error occurred").
    //
    // The decode runs off the main thread. It used to sit right here, in the result callback,
    // where a large photo froze the UI for the better part of a second — see decodeScanImage.
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && !state.scanned) {
            scope.launch {
                val image = withContext(Dispatchers.IO) { decodeScanImage(context, uri) }
                if (image == null) {
                    onNoQrCodeFound()
                    return@launch
                }
                val client = BarcodeScanning.getClient()
                client.process(image)
                    .addOnSuccessListener { barcodes ->
                        val url = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
                        if (url != null) {
                            state.scanned = true
                            onScanned(url)
                        } else {
                            onNoQrCodeFound()
                        }
                    }
                    .addOnFailureListener { onScanError() }
                    .addOnCompleteListener { client.close() }
            }
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

// The longest side a picked photo is decoded to. Checked on device against an 8160x6120 image,
// which samples down by 4: a QR occupying 1500 px of it (375 px after sampling) reads, and so
// does one occupying only 600 px (150 px after sampling). Real photos leave more margin than that.
private const val MAX_SCAN_IMAGE_PX = 2048

/**
 * Decodes a picked gallery image down to [MAX_SCAN_IMAGE_PX] instead of handing the Uri to
 * InputImage.fromFilePath.
 *
 * That helper goes through MediaStore.Images.Media.getBitmap, which decodes at full resolution
 * with no sampling, and then — when EXIF says the photo is rotated — builds a second full-size
 * copy before recycling the first. Measured on a Galaxy S20 with a synthetic 50 MP photo:
 * peak RSS +221 MB unrotated, +393 MB rotated (760 MB absolute), and "Skipped 42 frames"
 * because the decode ran on the main thread. Sampling first brings the peak to ~12 MB.
 *
 * Orientation is passed to ML Kit as an angle rather than baked into the pixels, so the rotated
 * case costs no extra bitmap. The four mirrored orientations cannot be expressed as an angle and
 * do get one copy — of the already sampled bitmap, and only for orientations cameras rarely write.
 * That copy keeps the normalisation the old path did for them; it is not what makes detection
 * work. Detection needs neither: on device ML Kit read the same QR rotated 90 degrees with no EXIF
 * tag at all, and read it mirrored both with and without the tag that says so.
 *
 * runCatching is deliberately broad: it also swallows OutOfMemoryError, which sampling makes
 * unlikely but a malformed image could still provoke. The caller reports it as "no QR found".
 */
private fun decodeScanImage(context: Context, uri: Uri): InputImage? = runCatching {
    val resolver = context.contentResolver

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
    }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: return@runCatching null

    inputImageFor(bitmap, exifOrientation(resolver, uri))
}.getOrNull()

/** Smallest power of two that brings the longest side within [MAX_SCAN_IMAGE_PX]. */
private fun sampleSizeFor(width: Int, height: Int): Int {
    var sample = 1
    while (maxOf(width, height) / sample > MAX_SCAN_IMAGE_PX) sample *= 2
    return sample
}

private fun exifOrientation(resolver: ContentResolver, uri: Uri): Int =
    resolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
    } ?: ExifInterface.ORIENTATION_NORMAL

private fun inputImageFor(bitmap: Bitmap, orientation: Int): InputImage = when (orientation) {
    ExifInterface.ORIENTATION_ROTATE_90 -> InputImage.fromBitmap(bitmap, 90)
    ExifInterface.ORIENTATION_ROTATE_180 -> InputImage.fromBitmap(bitmap, 180)
    ExifInterface.ORIENTATION_ROTATE_270 -> InputImage.fromBitmap(bitmap, 270)
    // Each mirrored orientation is a horizontal mirror followed by a rotation.
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> InputImage.fromBitmap(bitmap.mirrored(), 0)
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> InputImage.fromBitmap(bitmap.mirrored(), 180)
    ExifInterface.ORIENTATION_TRANSPOSE -> InputImage.fromBitmap(bitmap.mirrored(), 270)
    ExifInterface.ORIENTATION_TRANSVERSE -> InputImage.fromBitmap(bitmap.mirrored(), 90)
    else -> InputImage.fromBitmap(bitmap, 0)
}

private fun Bitmap.mirrored(): Bitmap {
    val matrix = Matrix().apply { postScale(-1f, 1f) }
    val copy = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (copy !== this) recycle()
    return copy
}
