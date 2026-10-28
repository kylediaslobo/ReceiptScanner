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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyled.receiptscanner.data.AuthRepository
import com.kyled.receiptscanner.data.ProfileStore
import com.kyled.receiptscanner.data.Receipt
import com.kyled.receiptscanner.data.ReceiptRepository
import com.kyled.receiptscanner.data.formatDay
import com.kyled.receiptscanner.data.formatRand
import com.kyled.receiptscanner.data.label
import com.kyled.receiptscanner.data.month
import com.kyled.receiptscanner.data.toUserMessage
import com.kyled.receiptscanner.data.total
import com.kyled.receiptscanner.export.claimFileName
import com.kyled.receiptscanner.export.writeClaimForm
import com.kyled.receiptscanner.ui.container
import com.kyled.receiptscanner.ui.theme.ReceiptType
import java.io.File
import java.time.YearMonth
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
    /** The month just exported, while asking whether to delete or keep its receipts. */
    val exportedMonth: YearMonth? = null,
) {
    /** Receipts by the month of their expense date, newest month first. */
    val months: List<Pair<YearMonth, List<Receipt>>>
        get() = receipts.groupBy { it.month }.toList().sortedByDescending { it.first }
}

class ReceiptListViewModel(
    private val repo: ReceiptRepository,
    private val auth: AuthRepository,
    private val profile: ProfileStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ReceiptListUiState())
    val state = _state.asStateFlow()

    val fullName = profile.fullName

    fun setFullName(name: String) = profile.setFullName(name)

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

    /** Writes [month]'s receipts onto the claim form under [claimant]'s name. */
    fun export(month: YearMonth, claimant: String) {
        if (_state.value.exporting) return
        profile.setFullName(claimant)
        _state.update { it.copy(exporting = true) }
        viewModelScope.launch {
            try {
                val receipts = repo.list().filter { it.month == month }
                if (receipts.isEmpty()) {
                    _state.update { it.copy(exporting = false, message = "There are no receipts for ${month.label()}") }
                    return@launch
                }
                val file = File(repo.exportsDir, claimFileName(month))
                withContext(Dispatchers.IO) { writeClaimForm(claimant.trim(), month, receipts, file) }
                _state.update { it.copy(exporting = false, exportFile = file, exportedMonth = month) }
            } catch (e: Exception) {
                _state.update { it.copy(exporting = false, message = e.toUserMessage()) }
            }
        }
    }

    fun exportHandled() = _state.update { it.copy(exportFile = null) }

    fun keepExportedMonth() = _state.update { it.copy(exportedMonth = null) }

    fun deleteExportedMonth() {
        val month = _state.value.exportedMonth ?: return
        val count = _state.value.receipts.count { it.month == month }
        _state.update { it.copy(exportedMonth = null) }
        viewModelScope.launch {
            try {
                repo.deleteMonth(month)
                _state.update { it.copy(message = "Deleted $count receipts from ${month.label()}") }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(message = e.toUserMessage()) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun signOut() {
        viewModelScope.launch { runCatching { auth.signOut() } }
    }
}

/**
 * The home screen: receipts grouped by month. On wide screens (tablets, unfolded foldables,
 * landscape) it shows the list and the selected receipt side by side; on phones, tapping a receipt
 * opens it full-screen via [onOpen].
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
        ReceiptListViewModel(c.receiptRepository, c.authRepository, c.profile)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val fullName by vm.fullName.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportOpen by rememberSaveable { mutableStateOf(false) }
    var nameOpen by rememberSaveable { mutableStateOf(false) }
    var askedForName by rememberSaveable { mutableStateOf(false) }

    // Runs each time the list comes back into view, so saves and edits show up.
    LaunchedEffect(Unit) { vm.refresh() }
    // Ask once on first run, so new receipts have a default for Names.
    LaunchedEffect(fullName) {
        if (fullName.isBlank() && !askedForName) {
            askedForName = true
            nameOpen = true
        }
    }
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
                        IconButton(onClick = { exportOpen = true }, enabled = state.receipts.isNotEmpty()) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = "Export to Excel")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Your name") },
                                onClick = { menuOpen = false; nameOpen = true },
                            )
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
                            state.months.forEach { (month, receipts) ->
                                item(key = month.toString()) { MonthHeader(month, receipts) }
                                items(receipts, key = { it.id }) { receipt ->
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

    if (exportOpen) {
        ExportDialog(
            months = state.months,
            fullName = fullName,
            onDismiss = { exportOpen = false },
            onExport = { month, name ->
                exportOpen = false
                vm.export(month, name)
            },
        )
    }

    if (nameOpen) {
        NameDialog(
            current = fullName,
            onDismiss = { nameOpen = false },
            onSave = {
                vm.setFullName(it)
                nameOpen = false
            },
        )
    }

    // Once the file has gone to the share sheet, ask what to do with that month's receipts.
    val exportedMonth = state.exportedMonth
    if (exportedMonth != null && state.exportFile == null) {
        val count = state.receipts.count { it.month == exportedMonth }
        AlertDialog(
            onDismissRequest = vm::keepExportedMonth,
            title = { Text("Delete ${exportedMonth.label()}?") },
            text = {
                Text(
                    "${exportedMonth.label()} has been exported with $count receipts. Delete them from the app " +
                        "now, or keep them in case you need to export again?",
                )
            },
            confirmButton = {
                TextButton(onClick = vm::deleteExportedMonth) {
                    Text("Delete $count", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::keepExportedMonth) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun MonthHeader(month: YearMonth, receipts: List<Receipt>) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(month.label(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                "${receipts.size} · ${formatRand(receipts.total())}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
                    receipt.natureOfExpense,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(formatDay(receipt.expenseDate), receipt.company, receipt.names).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                formatRand(receipt.amount),
                style = ReceiptType.body.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Pick the month to export and confirm the name printed on the form. */
@Composable
private fun ExportDialog(
    months: List<Pair<YearMonth, List<Receipt>>>,
    fullName: String,
    onDismiss: () -> Unit,
    onExport: (YearMonth, String) -> Unit,
) {
    // Claims usually go in early in the month, for the month just ended.
    var selected by rememberSaveable {
        val lastMonth = YearMonth.now().minusMonths(1)
        mutableStateOf((months.firstOrNull { it.first == lastMonth } ?: months.first()).first.toString())
    }
    var name by rememberSaveable { mutableStateOf(fullName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export claim form") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name on the form") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text("Month", style = MaterialTheme.typography.labelLarge)
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    months.forEach { (month, receipts) ->
                        val key = month.toString()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = selected == key, onClick = { selected = key }, role = Role.RadioButton),
                        ) {
                            RadioButton(selected = selected == key, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text(month.label(), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${receipts.size} receipts · ${formatRand(receipts.total())}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(YearMonth.parse(selected), name) }, enabled = name.isNotBlank()) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** The claimant's full name: printed on the form, and its first word fills each receipt's Names. */
@Composable
private fun NameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name") },
        text = {
            Column {
                Text(
                    "As it should appear on the claim form. Your first name fills in Names on each new receipt.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Full name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later") }
        },
    )
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
            "Take a photo and the date and amount are filled in for you.",
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
        startActivity(Intent.createChooser(send, "Export claim form"))
    } catch (e: ActivityNotFoundException) {
        // No app can receive the file; nothing else to do.
    }
}
