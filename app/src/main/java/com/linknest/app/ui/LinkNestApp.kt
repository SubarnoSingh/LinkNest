package com.linknest.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linknest.app.data.repository.BookmarkDraft
import com.linknest.app.data.repository.NoteDraft
import com.linknest.app.data.repository.SaveBookmarkResult
import com.linknest.app.data.repository.SaveNoteResult
import com.linknest.app.domain.Bookmark
import com.linknest.app.domain.LibraryItem
import com.linknest.app.domain.Note
import com.linknest.app.presentation.MainViewModel
import com.linknest.app.presentation.BackupOperation
import com.linknest.app.ui.screens.BackupRestoreDialog
import com.linknest.app.ui.screens.BookmarkEditorDialog
import com.linknest.app.ui.screens.BookmarkDetailDialog
import com.linknest.app.ui.screens.GraphScreen
import com.linknest.app.ui.screens.LibraryScreen
import com.linknest.app.ui.screens.NoteEditorDialog
import com.linknest.app.ui.screens.NoteDetailDialog
import com.linknest.app.ui.screens.NotesScreen
import com.linknest.app.ui.screens.NodeConnectionDialog
import com.linknest.app.ui.screens.RestoreConfirmationDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AppTab { LIBRARY, GRAPH, NOTES }

@Composable
fun LinkNestApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableStateOf(AppTab.LIBRARY) }
    var showBookmarkEditor by rememberSaveable { mutableStateOf(false) }
    var editingBookmarkId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showNoteEditor by rememberSaveable { mutableStateOf(false) }
    var editingNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var sharedNoteDraft by remember { mutableStateOf<Note?>(null) }
    var detailBookmarkId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var connectionSource by remember { mutableStateOf<String?>(null) }
    var showBackupRestore by rememberSaveable { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pickerActive by remember { mutableStateOf(false) }
    val editingBookmark = editingBookmarkId?.let { id -> state.content.bookmarks.firstOrNull { it.id == id } }
    val editingNote = editingNoteId?.let { id -> state.content.notes.firstOrNull { it.id == id } } ?: sharedNoteDraft
    val detailBookmark = detailBookmarkId?.let { id -> state.content.bookmarks.firstOrNull { it.id == id } }
    val detailNote = detailNoteId?.let { id -> state.content.notes.firstOrNull { it.id == id } }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        pickerActive = false
        if (uri != null) viewModel.exportBackup(uri, context.contentResolver)
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        pickerActive = false
        if (uri != null) pendingRestoreUri = uri
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(state.pendingShare) {
        state.pendingShare?.let { shared ->
            if (shared.url == null) {
                showBookmarkEditor = false
                detailBookmarkId = null
                detailNoteId = null
                connectionSource = null
                editingBookmarkId = null
                editingNoteId = null
                sharedNoteDraft = Note(
                    id = 0,
                    title = shared.suggestedTitle.ifBlank { "Shared thought" },
                    body = shared.rawText,
                    createdAt = 0,
                    updatedAt = 0,
                    tags = emptyList()
                )
                showNoteEditor = true
            } else {
                showNoteEditor = false
                detailBookmarkId = null
                detailNoteId = null
                connectionSource = null
                editingNoteId = null
                sharedNoteDraft = null
                editingBookmarkId = null
                showBookmarkEditor = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == AppTab.LIBRARY,
                    onClick = { currentTab = AppTab.LIBRARY },
                    icon = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null) },
                    label = { androidx.compose.material3.Text("Library") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.GRAPH,
                    onClick = { currentTab = AppTab.GRAPH },
                    icon = { Icon(Icons.Outlined.GraphicEq, contentDescription = null) },
                    label = { androidx.compose.material3.Text("Graph") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.NOTES,
                    onClick = { currentTab = AppTab.NOTES },
                    icon = { Icon(Icons.Outlined.Notes, contentDescription = null) },
                    label = { androidx.compose.material3.Text("Notes") }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize()) {
            when (currentTab) {
                AppTab.LIBRARY -> LibraryScreen(
                    state = state,
                    viewModel = viewModel,
                    contentPadding = padding,
                    onAdd = { editingBookmarkId = null; showBookmarkEditor = true },
                    onOpenBookmark = { detailBookmarkId = it.id },
                    onOpenNote = { detailNoteId = it.id },
                    onBackupRestore = {
                        viewModel.clearBackupFeedback()
                        showBackupRestore = true
                    }
                )
                AppTab.GRAPH -> GraphScreen(
                    state = state,
                    viewModel = viewModel,
                    contentPadding = padding,
                    onOpenBookmark = { detailBookmarkId = it.id },
                    onOpenNote = { detailNoteId = it.id },
                    onConnect = { connectionSource = it }
                )
                AppTab.NOTES -> NotesScreen(
                    state = state,
                    viewModel = viewModel,
                    contentPadding = padding,
                    onAdd = { editingNoteId = null; sharedNoteDraft = null; showNoteEditor = true },
                    onOpenNote = { detailNoteId = it.id }
                )
            }
        }
    }

    if (showBookmarkEditor) {
        val shared = state.pendingShare
        BookmarkEditorDialog(
            initial = editingBookmark,
            sharedUrl = shared?.url,
            sharedTitle = shared?.suggestedTitle,
            sharedNote = shared?.note,
            onDismiss = {
                showBookmarkEditor = false
                editingBookmarkId = null
                viewModel.clearPendingShare()
            },
            onSave = { draft, done ->
                viewModel.saveBookmark(draft) { result ->
                    when (result) {
                        is SaveBookmarkResult.Saved -> {
                            done(null)
                            showBookmarkEditor = false
                            editingBookmarkId = null
                            viewModel.clearPendingShare()
                        }
                        is SaveBookmarkResult.Duplicate -> done("Already saved as \"${result.existing.title}\".")
                        is SaveBookmarkResult.Invalid -> done(result.message)
                    }
                }
            }
        )
    }

    if (showNoteEditor) {
        NoteEditorDialog(
            initial = editingNote,
            onDismiss = { showNoteEditor = false; editingNoteId = null; sharedNoteDraft = null; viewModel.clearPendingShare() },
            onSave = { draft, done ->
                viewModel.saveNote(draft) { result ->
                    when (result) {
                        is SaveNoteResult.Saved -> { done(null); showNoteEditor = false; editingNoteId = null; sharedNoteDraft = null; viewModel.clearPendingShare() }
                        is SaveNoteResult.Invalid -> done(result.message)
                    }
                }
            }
        )
    }

        detailBookmark?.let { bookmark ->
        BookmarkDetailDialog(
            bookmark = bookmark,
            onDismiss = { detailBookmarkId = null },
            onEdit = { detailBookmarkId = null; editingBookmarkId = bookmark.id; showBookmarkEditor = true },
            onDelete = { viewModel.deleteBookmark(bookmark.id); detailBookmarkId = null },
            onFavorite = { viewModel.toggleFavorite(bookmark.id) },
            onConnect = { connectionSource = "bookmark:${bookmark.id}" },
            onOpen = { openUrl(context, bookmark.originalUrl) },
            metadataState = state.metadataStates[bookmark.id] ?: com.linknest.app.presentation.MetadataUiState(),
            onRetryMetadata = { viewModel.retryMetadata(bookmark.id) }
        )
    }

    detailNote?.let { note ->
        NoteDetailDialog(
            note = note,
            onDismiss = { detailNoteId = null },
            onEdit = { detailNoteId = null; editingNoteId = note.id; sharedNoteDraft = null; showNoteEditor = true },
            onDelete = { viewModel.deleteNote(note.id); detailNoteId = null },
            onConnect = { connectionSource = "note:${note.id}" }
        )
    }

    if (showBackupRestore) {
        BackupRestoreDialog(
            state = state.backupState,
            onDismiss = { showBackupRestore = false },
            onExport = {
                if (!pickerActive && state.backupState.operation == BackupOperation.IDLE) {
                    pickerActive = true
                    exportLauncher.launch(defaultBackupFileName())
                }
            },
            onRestore = {
                if (!pickerActive && state.backupState.operation == BackupOperation.IDLE) {
                    pickerActive = true
                    restoreLauncher.launch(arrayOf("application/json", "text/plain"))
                }
            }
        )
    }

    pendingRestoreUri?.let { uri ->
        RestoreConfirmationDialog(
            enabled = state.backupState.operation == BackupOperation.IDLE,
            onDismiss = { pendingRestoreUri = null },
            onConfirm = {
                pendingRestoreUri = null
                viewModel.restoreBackup(uri, context.contentResolver)
            }
        )
    }

    connectionSource?.let { source ->
        NodeConnectionDialog(
            sourceKey = source,
            state = state,
            onDismiss = { connectionSource = null },
            onConnect = { target ->
                viewModel.addEdge(source, target) { result ->
                    if (result.isSuccess) connectionSource = null
                }
            }
        )
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure { error ->
        if (error is ActivityNotFoundException) {
            // No browser is an unusual device configuration; the detail screen remains usable offline.
        }
    }
}

private fun defaultBackupFileName(): String =
    "linknest-backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.json"
