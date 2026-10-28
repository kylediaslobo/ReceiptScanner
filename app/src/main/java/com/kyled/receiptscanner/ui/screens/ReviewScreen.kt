package com.kyled.receiptscanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyled.receiptscanner.data.ProfileStore
import com.kyled.receiptscanner.data.ReceiptDraft
import com.kyled.receiptscanner.data.ReceiptRepository
import com.kyled.receiptscanner.data.firstName
import com.kyled.receiptscanner.data.toDraft
import com.kyled.receiptscanner.data.toUserMessage
import com.kyled.receiptscanner.data.validate
import com.kyled.receiptscanner.ui.components.PaperReceipt
import com.kyled.receiptscanner.ui.components.PhotoDialog
import com.kyled.receiptscanner.ui.components.PhotoSource
import com.kyled.receiptscanner.ui.components.Suggestions
import com.kyled.receiptscanner.ui.container
import com.kyled.receiptscanner.ui.theme.OnSaveGreen
import com.kyled.receiptscanner.ui.theme.SaveGreen
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewUiState(
    /** Non-null while uploading, reading or loading; shown as a progress message. */
    val working: String? = null,
    /** Non-null when the photo couldn't be uploaded or read. Offers retry or manual entry. */
    val loadError: String? = null,
    val draft: ReceiptDraft = ReceiptDraft(),
    val isEdit: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val savedId: String? = null,
    val localPhoto: File? = null,
    val dirty: Boolean = false,
    val suggestions: Suggestions = Suggestions(),
)

/**
 * Three ways in, set by the navigation arguments:
 *  - file: a fresh camera capture to upload, then read
 *  - path: a photo already in storage (recovered from "Recent photos") to read again
 *  - id:   an existing receipt to edit
 */
class ReviewViewModel(
    private val repo: ReceiptRepository,
    profile: ProfileStore,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val captureFile: File? = savedState.get<String>("file")?.let { File(repo.capturesDir, it) }
    private var imagePath: String? = savedState.get<String>("path")
    private val receiptId: String? = savedState.get<String>("id")
    private val defaultNames: String = profile.fullName.value.firstName()

    private val _state = MutableStateFlow(ReviewUiState(isEdit = receiptId != null, localPhoto = captureFile))
    val state = _state.asStateFlow()

    init {
        load()
        loadSuggestions()
    }

    fun retry() = load()

    private fun load() {
        _state.update { it.copy(loadError = null) }
        viewModelScope.launch {
            try {
                if (receiptId != null) {
                    _state.update { it.copy(working = "Loading receipt…") }
                    val receipt = repo.get(receiptId)
                    _state.update { it.copy(working = null, draft = receipt.toDraft()) }
                    return@launch
                }
                if (imagePath == null && captureFile != null) {
                    _state.update { it.copy(working = "Uploading photo…") }
                    imagePath = repo.uploadImage(captureFile)
                }
                val path = imagePath ?: error("No photo to read")
                _state.update { it.copy(working = "Reading receipt…") }
                val extracted = repo.extract(path)
                _state.update { it.copy(working = null, draft = extracted.toDraft(defaultNames).copy(imagePath = path)) }
            } catch (e: Exception) {
                _state.update { it.copy(working = null, loadError = e.toUserMessage()) }
            }
        }
    }

    /** Past values for the prompted fields, most used first, topped up with common ones. Best effort. */
    private fun loadSuggestions() {
        viewModelScope.launch {
            val past = runCatching { repo.pastChoices() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    suggestions = Suggestions(
                        natureOfExpense = rankByUse(past.map { c -> c.natureOfExpense } + COMMON_EXPENSES),
                        company = rankByUse(past.map { c -> c.company }),
                    ),
                )
            }
        }
    }

    /** Skip extraction and fill the receipt in by hand. Keeps the uploaded photo attached if there is one. */
    fun enterByHand() {
        _state.update { it.copy(loadError = null, draft = ReceiptDraft(names = defaultNames, imagePath = imagePath)) }
    }

    fun update(draft: ReceiptDraft) {
        _state.update { it.copy(draft = draft, dirty = true) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun save() {
        val draft = _state.value.draft
        draft.validate()?.let { problem ->
            _state.update { it.copy(message = problem) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val id = repo.save(draft)
                captureFile?.delete()
                _state.update { it.copy(saving = false, savedId = id, dirty = false) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, message = e.toUserMessage()) }
            }
        }
    }

    private companion object {
        val COMMON_EXPENSES = listOf("Breakfast", "Lunch", "Supper", "Snack", "Airport Parking", "Accommodation")

        /** Distinct values, the most frequent first; ties keep their original (newest-first) order. */
        fun rankByUse(values: List<String>): List<String> {
            val counts = values.map { it.trim() }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
            return counts.keys.sortedByDescending { counts.getValue(it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val vm: ReviewViewModel = viewModel {
        val c = container()
        ReviewViewModel(c.receiptRepository, c.profile, createSavedStateHandle())
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showPhoto by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val leave: () -> Unit = {
        if (state.dirty) {
            confirmDiscard = true
        } else {
            onBack()
        }
    }
    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Edit receipt" else "Review") },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.localPhoto != null || state.draft.imagePath != null) {
                        IconButton(onClick = { showPhoto = true }) {
                            Icon(Icons.Outlined.Image, contentDescription = "View photo")
                        }
                    }
                    val canSave = state.working == null && state.loadError == null
                    Button(
                        onClick = vm::save,
                        enabled = canSave && !state.saving,
                        colors = ButtonDefaults.buttonColors(containerColor = SaveGreen, contentColor = OnSaveGreen),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(color = OnSaveGreen, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Save")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.working != null -> WorkingState(state.working!!)
                state.loadError != null -> LoadErrorState(
                    message = state.loadError!!,
                    onRetry = vm::retry,
                    onEnterByHand = vm::enterByHand,
                )
                else -> ReviewForm(draft = state.draft, suggestions = state.suggestions, onChange = vm::update)
            }
        }
    }

    if (showPhoto) {
        val source = state.localPhoto?.takeIf { it.exists() }?.let { PhotoSource.Local(it) }
            ?: state.draft.imagePath?.let { PhotoSource.Stored(it) }
        if (source != null) PhotoDialog(source = source, onDismiss = { showPhoto = false })
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this receipt won't be saved.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onBack() }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
            },
        )
    }
}

@Composable
private fun ReviewForm(draft: ReceiptDraft, suggestions: Suggestions, onChange: (ReceiptDraft) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PaperReceipt(draft = draft, editable = true, onChange = onChange, suggestions = suggestions)
        Text(
            "Check the date and amount, then fill in what it was for and the company.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(top = 8.dp),
        )
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun WorkingState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LoadErrorState(message: String, onRetry: () -> Unit, onEnterByHand: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onEnterByHand) { Text("Enter by hand") }
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}
