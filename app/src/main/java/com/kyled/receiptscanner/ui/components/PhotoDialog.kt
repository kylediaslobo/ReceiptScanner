package com.kyled.receiptscanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.kyled.receiptscanner.ReceiptApp
import java.io.File

sealed interface PhotoSource {
    data class Local(val file: File) : PhotoSource
    data class Stored(val path: String) : PhotoSource
}

/** Full-screen view of the original photo, so extracted values can be checked against it. */
@Composable
fun PhotoDialog(source: PhotoSource, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val model by produceState<Any?>(initialValue = (source as? PhotoSource.Local)?.file, source) {
        if (source is PhotoSource.Stored) {
            val repo = (context.applicationContext as ReceiptApp).container.receiptRepository
            value = runCatching { repo.imageUrl(source.path) }.getOrElse { FAILED }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when (val m = model) {
                null -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
                FAILED -> Text(
                    "This photo is no longer stored. Photos are kept for 2 weeks.",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                else -> AsyncImage(
                    model = m,
                    contentDescription = "Receipt photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(8.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close")
            }
        }
    }
}

private val FAILED = Any()
