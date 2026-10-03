package com.emberr.presentation.sync

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

actual @Composable fun QrCodeDisplay(data: String, size: Int, modifier: Modifier) {
    Box(modifier.size(size.dp))
}

actual @Composable fun QrScannerView(
    onQrScanned: (String) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasCameraPermission) {
        val executor = remember { Executors.newSingleThreadExecutor() }
        DisposableEffect(Unit) {
            onDispose {
                executor.shutdown()
            }
        }

        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val frameDecoder = QrCodeFrameDecoder()
                        val mainThreadExecutor = ContextCompat.getMainExecutor(ctx)

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            try {
                                val luminancePlane = imageProxy.planes[0]
                                val rowStride = luminancePlane.rowStride
                                val luminanceBytes = ByteArray(rowStride * imageProxy.height)
                                luminancePlane.buffer.get(luminanceBytes, 0, luminancePlane.buffer.remaining())
                                frameDecoder.decode(luminanceBytes, rowStride, imageProxy.width, imageProxy.height)
                                    ?.let { scannedData ->
                                        mainThreadExecutor.execute { onQrScanned(scannedData) }
                                    }
                            } catch (frameFailure: Exception) {
                                Log.e("QrScanner", "Could not read a camera frame", frameFailure)
                            } finally {
                                imageProxy.close()
                            }
                        }

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (startFailure: Exception) {
                        Log.e("QrScanner", "Could not start the camera scanner", startFailure)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            onRelease = { _ ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        cameraProviderFuture.get().unbindAll()
                    } catch (releaseFailure: Exception) {
                        Log.e("QrScanner", "Could not release the camera", releaseFailure)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )
    } else {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is required to scan pairing codes.")
        }
    }
}