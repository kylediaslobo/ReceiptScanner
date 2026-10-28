package com.kyled.receiptscanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyled.receiptscanner.data.Receipt
import com.kyled.receiptscanner.data.ReceiptRepository
import com.kyled.receiptscanner.data.toDraft
import com.kyled.receiptscanner.data.toUserMessage
import com.kyled.receiptscanner.ui.components.PaperReceipt
import com.kyled.receiptscanner.ui.components.PhotoDialog
import com.kyled.receiptscanner.ui.components.PhotoSource
import com.kyled.receiptscanner.ui.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val receipt: Receipt? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val deleted: Boolean = false,
)

class ReceiptDetailViewModel(private val repo: ReceiptRepository, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow(DetailUiState())
    val state = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = it.receipt == null, error = null) }
        viewModelScope.launch {
            try {
                val receipt = repo.get(id)
                _state.update { it.copy(receipt = receipt, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                repo.delete(id)
                _state.update { it.copy(deleted = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toUserMessage()) }
            }
        }
    }
}

/**
 * One saved receipt, read-only. Used full-screen on phones ([onBack] set) and as the right-hand pane
 * on tablets ([onBack] null).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(
    receiptId: String,
    onBack: (() -> Unit)?,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
) {
    val vm: ReceiptDetailViewModel = viewModel(key = "detail-$receiptId") {
        ReceiptDetailViewModel(container().receiptRepository, receiptId)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showPhoto by rememberSaveable { mutableStateOf(false) }

    // Re-runs whenever this screen comes back into view, e.g. after editing.
    LaunchedEffect(receiptId) { vm.load() }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.receipt?.natureOfExpense.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    state.receipt?.let { receipt ->
                        if (receipt.imagePath != null) {
                            IconButton(onClick = { showPhoto = true }) {
                                Icon(Icons.Outlined.Image, contentDescription = "View photo")
                            }
                        }
                        IconButton(onClick = { onEdit(receipt.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val receipt = state.receipt
            when {
                receipt != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PaperReceipt(draft = receipt.toDraft(), editable = false, onChange = {})
                    Text(
                        "Saved ${receipt.createdAt.take(10)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(32.dp))
                }
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(state.error ?: "This receipt couldn't be loaded.")
                    Button(onClick = vm::load) { Text("Try again") }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this receipt?") },
            text = { Text("It will be removed from your account. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }

    if (showPhoto) {
        state.receipt?.imagePath?.let { PhotoDialog(PhotoSource.Stored(it), onDismiss = { showPhoto = false }) }
    }
}
