package com.kyled.receiptscanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kyled.receiptscanner.data.ReceiptRepository
import com.kyled.receiptscanner.data.toUserMessage
import com.kyled.receiptscanner.ui.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecentPhoto(val path: String, val savedOn: String, val url: String?)

data class RecentPhotosUiState(
    val photos: List<RecentPhoto> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class RecentPhotosViewModel(private val repo: ReceiptRepository) : ViewModel() {
    private val _state = MutableStateFlow(RecentPhotosUiState())
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            try {
                val photos = repo.recentImages().map { image ->
                    RecentPhoto(
                        path = image.name,
                        savedOn = image.createdAt.take(10),
                        url = runCatching { repo.imageUrl(image.name) }.getOrNull(),
                    )
                }
                _state.update { RecentPhotosUiState(photos = photos, loading = false) }
            } catch (e: Exception) {
                _state.update { RecentPhotosUiState(loading = false, error = e.toUserMessage()) }
            }
        }
    }
}

/**
 * Recovery: the last 15 photos (kept for up to 2 weeks). Tapping one reads it again and opens the
 * review screen, e.g. if a save failed or a receipt was deleted by mistake.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentPhotosScreen(onBack: () -> Unit, onPick: (String) -> Unit) {
    val vm: RecentPhotosViewModel = viewModel { RecentPhotosViewModel(container().receiptRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recent photos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(state.error!!, modifier = Modifier.align(Alignment.Center).padding(32.dp))
                state.photos.isEmpty() -> Text(
                    "No photos stored. The last 15 photos are kept for 2 weeks.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                else -> Column {
                    Text(
                        "Tap a photo to read it again. The last 15 are kept for 2 weeks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.photos, key = { it.path }) { photo ->
                            Column(
                                Modifier
                                    .padding(4.dp)
                                    .clickable { onPick(photo.path) },
                            ) {
                                AsyncImage(
                                    model = photo.url,
                                    contentDescription = "Receipt photo from ${photo.savedOn}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(3f / 4f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                Text(
                                    photo.savedOn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
