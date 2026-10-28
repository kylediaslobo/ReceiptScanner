package com.kyled.receiptscanner.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyled.receiptscanner.data.AuthRepository
import com.kyled.receiptscanner.data.Receipt
import com.kyled.receiptscanner.data.ReceiptRepository
import com.kyled.receiptscanner.data.toMoneyText
import com.kyled.receiptscanner.data.toUserMessage
import com.kyled.receiptscanner.export.writeReceiptsWorkbook
import com.kyled.receiptscanner.ui.container
import com.kyled.receiptscanner.ui.theme.ReceiptType
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReceiptListUiState(
    val receipts: List<Receipt> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val exporting: Boolean = false,
    val message: String? = null,
    /** Set when an export is ready to hand to the share sheet; cleared once shared. */
    val exportFile: File? = null,
)

class ReceiptListViewModel(
    private val repo: ReceiptRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReceiptListUiState())
    val state = _state.asStateFlow()

    fun refresh(userInitiated: Boolean = false) {
        _state.update { it.copy(refreshing = userInitiated) }
        viewModelScope.launch {
            try {
                val receipts = repo.list()
                _state.update { it.copy(receipts = receipts, loading = false, refreshing = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, refreshing = false, message = e.toUserMessage()) }
            }
        }
    }

    fun export() {
        if (_state.value.exporting) return
        _state.update { it.copy(exporting = true) }
        viewModelScope.launch {
            try {
                val receipts = repo.list()
                if (receipts.isEmpty()) {
                    _state.update { it.copy(exporting = false, message = "There are no receipts to export yet") }
                    return@launch
                }
                val file = File(repo.exportsDir, "receipts-${LocalDate.now()}.xlsx")
                withContext(Dispatchers.IO) { writeReceiptsWorkbook(receipts, file) }
                _state.update { it.copy(exporting = false, exportFile = file) }
            } catch (e: Exception) {
                _state.update { it.copy(exporting = false, message = e.toUserMessage()) }
            }
        }
    }

    fun exportHandled() = _state.update { it.copy(exportFile = null) }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun signOut() {
        viewModelScope.launch { runCatching { auth.signOut() } }
    }
}

/**
 * The home screen. On wide screens (tablets, unfolded foldables, landscape) it shows the list and the
 * selected receipt side by side; on phones, tapping a receipt opens it full-screen via [onOpen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptListScreen(
    twoPane: Boolean,
    onScan: () -> Unit,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onRecentPhotos: () -> Unit,
) {
    val vm: ReceiptListViewModel = viewModel {
        val c = container()
        ReceiptListViewModel(c.receiptRepository, c.authRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    // Runs each time the list comes back into view, so saves and edits show up.
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }
    LaunchedEffect(state.exportFile) {
        state.exportFile?.let { file ->
            context.shareSpreadsheet(file)
            vm.exportHandled()
        }
    }
    // Keep the right-hand pane pointing at something that still exists.
    LaunchedEffect(twoPane, state.receipts) {
        if (twoPane && state.receipts.none { it.id == selectedId }) selectedId = state.receipts.firstOrNull()?.id
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receipts") },
                actions = {
                    if (state.exporting) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(12.dp).size(24.dp))
                    } else {
                        IconButton(onClick = vm::export) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = "Export to Excel")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Recent photos") },
                                onClick = { menuOpen = false; onRecentPhotos() },
                            )
                            DropdownMenuItem(
                                text = { Text("Sign out") },
                                onClick = { menuOpen = false; vm.signOut() },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Outlined.CameraAlt, contentDescription = null) },
                text = { Text("Scan") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(if (twoPane) 0.42f else 1f).fillMaxHeight()) {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { vm.refresh(userInitiated = true) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        state.receipts.isEmpty() -> EmptyState(onScan)
                        else -> LazyColumn(
                            contentPadding = PaddingValues(bottom = 96.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.receipts, key = { it.id }) { receipt ->
                                ReceiptRow(
                                    receipt = receipt,
                                    selected = twoPane && receipt.id == selectedId,
                                    onClick = { if (twoPane) selectedId = receipt.id else onOpen(receipt.id) },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
            if (twoPane) {
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(0.58f).fillMaxHeight()) {
                    val id = selectedId
                    if (id != null) {
                        ReceiptDetailScreen(
                            receiptId = id,
                            onBack = null,
                            onEdit = onEdit,
                            onDeleted = {
                                selectedId = null
                                vm.refresh()
                            },
                        )
                    } else {
                        Text(
                            "Select a receipt",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(receipt: Receipt, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    receipt.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOfNotNull(receipt.merchantName, receipt.purchaseDate).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                listOfNotNull(receipt.currency, receipt.total.toMoneyText().ifEmpty { null }).joinToString(" "),
                style = ReceiptType.body.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun EmptyState(onScan: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.ReceiptLong,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Scan your first receipt", style = MaterialTheme.typography.titleMedium)
        Text(
            "Take a photo and the details are filled in for you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onScan) { Text("Scan a receipt") }
    }
}

private fun Context.shareSpreadsheet(file: File) {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        startActivity(Intent.createChooser(send, "Export receipts"))
    } catch (e: ActivityNotFoundException) {
        // No app can receive the file; nothing else to do.
    }
}
