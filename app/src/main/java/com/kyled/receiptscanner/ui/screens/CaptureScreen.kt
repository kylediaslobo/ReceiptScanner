package com.kyled.receiptscanner.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyled.receiptscanner.camera.saveForUpload
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Full-screen in-app camera. Hands the saved JPEG to [onCaptured]. */
@Composable
fun CaptureScreen(
    capturesDir: File,
    onCaptured: (File) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraView(capturesDir = capturesDir, onCaptured = onCaptured)
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "The camera is needed to photograph receipts.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Allow camera")
                }
            }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.systemBarsPadding().padding(8.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
        }
    }
}

@Composable
private fun CameraView(capturesDir: File, onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var flashOn by remember { mutableStateOf(false) }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    LaunchedEffect(flashOn) {
        controller.imageCaptureFlashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = error ?: "Fit the whole receipt in the frame",
                color = if (error != null) MaterialTheme.colorScheme.error else Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
            Box(contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = {
                        if (busy) return@IconButton
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                val file = takePhoto(controller, context.mainExecutorCompat(), capturesDir)
                                onCaptured(file)
                            } catch (e: Exception) {
                                error = "Couldn't take the photo. Try again."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier
                        .size(76.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(8.dp)
                        .background(Color.White, CircleShape),
                ) {
                    if (busy) CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }
            }
        }

        IconButton(
            onClick = { flashOn = !flashOn },
            modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(8.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) {
            Icon(
                if (flashOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                contentDescription = if (flashOn) "Flash on" else "Flash off",
            )
        }
    }
}

private fun android.content.Context.mainExecutorCompat() = ContextCompat.getMainExecutor(this)

private suspend fun takePhoto(
    controller: LifecycleCameraController,
    executor: java.util.concurrent.Executor,
    dir: File,
): File {
    val (bitmap, rotation) = suspendCancellableCoroutine<Pair<Bitmap, Int>> { cont ->
        controller.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val result = try {
                    image.toBitmap() to image.imageInfo.rotationDegrees
                } finally {
                    image.close()
                }
                cont.resume(result)
            }

            override fun onError(exception: ImageCaptureException) {
                cont.resumeWithException(exception)
            }
        })
    }
    return withContext(Dispatchers.Default) {
        try {
            saveForUpload(bitmap, rotation, dir)
        } finally {
            bitmap.recycle()
        }
    }
}
