package com.linknest.app.presentation

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linknest.app.data.backup.BackupSummary
import com.linknest.app.data.backup.LinkNestBackupService
import com.linknest.app.data.metadata.DefaultPageMetadataFetcher
import com.linknest.app.data.metadata.PageMetadataFetcher
import com.linknest.app.data.repository.BookmarkDraft
import com.linknest.app.data.repository.ContentSnapshot
import com.linknest.app.data.repository.LinkNestRepository
import com.linknest.app.data.repository.NoteDraft
import com.linknest.app.data.repository.SaveBookmarkResult
import com.linknest.app.data.repository.SaveNoteResult
import com.linknest.app.domain.Bookmark
import com.linknest.app.domain.GraphBuilder
import com.linknest.app.domain.GraphEdgeType
import com.linknest.app.domain.GraphNodeType
import com.linknest.app.domain.LibraryItem
import com.linknest.app.domain.Note
import com.linknest.app.domain.ResourceType
import com.linknest.app.domain.SharedContent
import com.linknest.app.domain.SharedContentParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

enum class LibraryFilter(val label: String) {
    ALL("All"),
    FAVORITES("Favorites"),
    ARTICLES("Articles"),
    VIDEOS("Videos"),
    REPOSITORIES("Repositories"),
    NOTES("Notes")
}

enum class LibrarySort(val label: String) {
    RECENTLY_SAVED("Recently saved"),
    RECENTLY_UPDATED("Recently updated"),
    ALPHABETICAL("Alphabetical")
}

enum class GraphFilter(val label: String) {
    ALL("All"),
    BOOKMARKS("Links"),
    NOTES("Notes")
}

enum class MetadataFetchState {
    IDLE,
    LOADING,
    SUCCESS,
    FAILED
}

enum class BackupOperation {
    IDLE,
    EXPORTING,
    RESTORING
}

data class BackupUiState(
    val operation: BackupOperation = BackupOperation.IDLE,
    val feedback: String? = null,
    val feedbackIsError: Boolean = false
)

data class MetadataUiState(
    val state: MetadataFetchState = MetadataFetchState.IDLE,
    val message: String? = null
)

data class MainUiState(
    val content: ContentSnapshot = ContentSnapshot(emptyList(), emptyList(), emptyList()),
    val libraryQuery: String = "",
    val libraryFilter: LibraryFilter = LibraryFilter.ALL,
    val librarySort: LibrarySort = LibrarySort.RECENTLY_SAVED,
    val notesQuery: String = "",
    val graphFilter: GraphFilter = GraphFilter.ALL,
    val graphTag: String? = null,
    val showTagEdges: Boolean = true,
    val pendingShare: SharedContent? = null,
    val metadataStates: Map<Long, MetadataUiState> = emptyMap(),
    val backupState: BackupUiState = BackupUiState()
)

class MainViewModel(
    private val repository: LinkNestRepository,
    private val metadataFetcher: PageMetadataFetcher = DefaultPageMetadataFetcher(),
    private val backupService: LinkNestBackupService
) : ViewModel() {
    private val controls = MutableStateFlow(MainUiState())
    private val metadataStates = MutableStateFlow<Map<Long, MetadataUiState>>(emptyMap())
    private val backupState = MutableStateFlow(BackupUiState())
    private val metadataJobs = mutableMapOf<String, Job>()
    val uiState: StateFlow<MainUiState> = combine(repository.content, controls, metadataStates, backupState) { content, current, metadata, backup ->
        val availableTags = (content.bookmarks.flatMap { it.tags } + content.notes.flatMap { it.tags }).toSet()
        current.copy(
            content = content,
            graphTag = current.graphTag?.takeIf { it in availableTags },
            metadataStates = metadata.filterKeys { id -> content.bookmarks.any { it.id == id } },
            backupState = backup
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun setLibraryQuery(value: String) = controls.update { it.copy(libraryQuery = value) }
    fun setLibraryFilter(value: LibraryFilter) = controls.update { it.copy(libraryFilter = value) }
    fun setLibrarySort(value: LibrarySort) = controls.update { it.copy(librarySort = value) }
    fun setNotesQuery(value: String) = controls.update { it.copy(notesQuery = value) }
    fun setGraphFilter(value: GraphFilter) = controls.update { it.copy(graphFilter = value) }
    fun setGraphTag(value: String?) = controls.update { it.copy(graphTag = value) }
    fun toggleTagEdges() = controls.update { it.copy(showTagEdges = !it.showTagEdges) }

    fun receiveSharedContent(text: String?, viewUrl: String? = null) {
        val parsed = SharedContentParser.parse(text, viewUrl)
        if (parsed == null) {
            _messages.tryEmit("Nothing shareable was found in that content.")
        } else {
            controls.update { it.copy(pendingShare = parsed) }
        }
    }

    fun clearPendingShare() = controls.update { it.copy(pendingShare = null) }

    fun clearBackupFeedback() {
        backupState.update { it.copy(feedback = null, feedbackIsError = false) }
    }

    fun exportBackup(uri: Uri, contentResolver: ContentResolver) {
        if (backupState.value.operation != BackupOperation.IDLE) return
        backupState.value = BackupUiState(operation = BackupOperation.EXPORTING)
        viewModelScope.launch {
            val result = runCatching {
                val output = contentResolver.openOutputStream(uri, "wt")
                    ?: error("The selected destination could not be opened.")
                output.use { backupService.writeExport(it) }
            }
            result.fold(
                onSuccess = { summary ->
                    val message = backupSuccessMessage("Backup exported", summary)
                    backupState.value = BackupUiState(feedback = message)
                    _messages.emit(message)
                },
                onFailure = { error ->
                    val message = backupErrorMessage(error)
                    backupState.value = BackupUiState(feedback = message, feedbackIsError = true)
                    _messages.emit(message)
                }
            )
        }
    }

    fun restoreBackup(uri: Uri, contentResolver: ContentResolver) {
        if (backupState.value.operation != BackupOperation.IDLE) return
        metadataJobs.values.toList().forEach { it.cancel() }
        metadataJobs.clear()
        metadataStates.value = emptyMap()
        backupState.value = BackupUiState(operation = BackupOperation.RESTORING)
        viewModelScope.launch {
            val result = runCatching {
                val input = contentResolver.openInputStream(uri)
                    ?: error("The selected backup could not be opened.")
                input.use { backupService.restore(it) }
            }
            result.fold(
                onSuccess = { summary ->
                    val message = backupSuccessMessage("Library restored", summary)
                    backupState.value = BackupUiState(feedback = message)
                    _messages.emit(message)
                },
                onFailure = { error ->
                    val message = backupErrorMessage(error)
                    backupState.value = BackupUiState(feedback = message, feedbackIsError = true)
                    _messages.emit(message)
                }
            )
        }
    }

    fun metadataState(bookmarkId: Long): MetadataUiState = metadataStates.value[bookmarkId] ?: MetadataUiState()

    fun retryMetadata(bookmarkId: Long) {
        uiState.value.content.bookmarks.firstOrNull { it.id == bookmarkId }?.let(::requestMetadata)
    }

    private fun requestMetadata(bookmark: Bookmark) {
        val key = bookmark.url
        if (metadataJobs[key]?.isActive == true) return
        metadataStates.update { it + (bookmark.id to MetadataUiState(MetadataFetchState.LOADING)) }
        val job = viewModelScope.launch {
            val result = try {
                metadataFetcher.fetch(bookmark.url)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
            if (result.isSuccess) {
                try {
                    val applied = repository.applyPageMetadata(bookmark.id, bookmark.updatedAt, result.getOrThrow())
                    metadataStates.update {
                        if (applied) {
                            it + (bookmark.id to MetadataUiState(MetadataFetchState.SUCCESS))
                        } else {
                            // A user edit or deletion won the race; do not report a stale result as success.
                            it - bookmark.id
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    metadataStates.update {
                        it + (bookmark.id to MetadataUiState(MetadataFetchState.FAILED, error.message ?: "Metadata unavailable"))
                    }
                }
            } else {
                metadataStates.update {
                    it + (bookmark.id to MetadataUiState(
                        MetadataFetchState.FAILED,
                        result.exceptionOrNull()?.message ?: "Metadata unavailable"
                    ))
                }
            }
        }
        metadataJobs[key] = job
        job.invokeOnCompletion {
            if (metadataJobs[key] === job) metadataJobs.remove(key)
        }
    }

    fun saveBookmark(draft: BookmarkDraft, onResult: (SaveBookmarkResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = try {
                repository.saveBookmark(draft)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                SaveBookmarkResult.Invalid("Unable to save this link right now.")
            }
            onResult(result)
            when (result) {
                is SaveBookmarkResult.Saved -> {
                    requestMetadata(result.bookmark)
                    _messages.emit("Saved to your library")
                }
                is SaveBookmarkResult.Duplicate -> _messages.emit("That link is already in your library")
                is SaveBookmarkResult.Invalid -> Unit
            }
        }
    }

    fun toggleFavorite(bookmarkId: Long) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(bookmarkId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _messages.emit("Unable to update favorite right now")
            }
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteBookmark(bookmarkId)
                _messages.emit("Link deleted")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _messages.emit("Unable to delete that link right now")
            }
        }
    }

    fun saveNote(draft: NoteDraft, onResult: (SaveNoteResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = try {
                repository.saveNote(draft)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                SaveNoteResult.Invalid("Unable to save this note right now.")
            }
            onResult(result)
            when (result) {
                is SaveNoteResult.Saved -> _messages.emit("Note saved")
                is SaveNoteResult.Invalid -> Unit
            }
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteNote(noteId)
                _messages.emit("Note deleted")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _messages.emit("Unable to delete that note right now")
            }
        }
    }

    fun addEdge(first: String, second: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = try {
                repository.addExplicitEdge(first, second)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
            onResult(result)
            if (result.isSuccess) {
                _messages.emit("Connection added")
            } else {
                _messages.emit(result.exceptionOrNull()?.message ?: "Unable to add that connection")
            }
        }
    }

    fun removeEdge(first: String, second: String) {
        viewModelScope.launch {
            try {
                repository.removeExplicitEdge(first, second)
                _messages.emit("Connection removed")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _messages.emit("Unable to remove that connection right now")
            }
        }
    }

    fun visibleLibraryItems(state: MainUiState): List<LibraryItem> {
        val query = state.libraryQuery.trim().lowercase()
        val bookmarkItems = state.content.bookmarks.map(LibraryItem::BookmarkItem)
        val noteItems = state.content.notes.map(LibraryItem::NoteItem)
        val filtered = (bookmarkItems + noteItems).filter { item ->
            val matchesFilter = when (state.libraryFilter) {
                LibraryFilter.ALL -> true
                LibraryFilter.FAVORITES -> (item as? LibraryItem.BookmarkItem)?.bookmark?.isFavorite == true
                LibraryFilter.ARTICLES -> (item as? LibraryItem.BookmarkItem)?.bookmark?.resourceType == ResourceType.ARTICLE
                LibraryFilter.VIDEOS -> (item as? LibraryItem.BookmarkItem)?.bookmark?.resourceType == ResourceType.VIDEO
                LibraryFilter.REPOSITORIES -> (item as? LibraryItem.BookmarkItem)?.bookmark?.resourceType == ResourceType.REPOSITORY
                LibraryFilter.NOTES -> item is LibraryItem.NoteItem
            }
            val matchesQuery = if (query.isBlank()) true else when (item) {
                is LibraryItem.BookmarkItem -> listOf(
                    item.bookmark.title,
                    item.bookmark.url,
                    item.bookmark.domain,
                    item.bookmark.description,
                    item.bookmark.userNote,
                    item.bookmark.tags.joinToString(" ")
                ).any { it.contains(query, ignoreCase = true) }
                is LibraryItem.NoteItem -> listOf(
                    item.note.title,
                    item.note.body,
                    item.note.tags.joinToString(" ")
                ).any { it.contains(query, ignoreCase = true) }
            }
            matchesFilter && matchesQuery
        }
        return when (state.librarySort) {
            LibrarySort.RECENTLY_SAVED -> filtered.sortedByDescending {
                when (it) {
                    is LibraryItem.BookmarkItem -> it.bookmark.createdAt
                    is LibraryItem.NoteItem -> it.note.createdAt
                }
            }
            LibrarySort.RECENTLY_UPDATED -> filtered.sortedByDescending(LibraryItem::updatedAt)
            LibrarySort.ALPHABETICAL -> filtered.sortedBy { it.title.lowercase() }
        }
    }

    fun visibleNotes(state: MainUiState): List<Note> {
        val query = state.notesQuery.trim()
        return state.content.notes
            .filter { query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) || it.tags.any { tag -> tag.contains(query, true) } }
            .sortedByDescending { it.updatedAt }
    }

    fun graphSnapshot(state: MainUiState) = GraphBuilder.build(
        bookmarks = if (state.graphFilter == GraphFilter.NOTES) emptyList() else state.content.bookmarks,
        notes = if (state.graphFilter == GraphFilter.BOOKMARKS) emptyList() else state.content.notes,
        explicitEdges = state.content.explicitEdges
    ).let { snapshot ->
        val taggedNodes = state.graphTag?.let { tag -> snapshot.nodes.filter { tag in it.tags } } ?: snapshot.nodes
        val nodeKeys = taggedNodes.map { it.key }.toSet()
        snapshot.copy(
            nodes = taggedNodes,
            edges = snapshot.edges.filter { edge ->
                edge.source in nodeKeys && edge.target in nodeKeys &&
                    (state.showTagEdges || edge.type == GraphEdgeType.EXPLICIT)
            }
        )
    }

    fun connectedNodes(state: MainUiState, key: String): List<String> = state.content.explicitEdges
        .filter { it.source == key || it.target == key }
        .map { if (it.source == key) it.target else it.source }

    fun findNode(state: MainUiState, key: String) = graphSnapshot(state).nodes.firstOrNull { it.key == key }

    private fun backupSuccessMessage(prefix: String, summary: BackupSummary): String =
        "$prefix: ${summary.itemCount} items, ${summary.tagCount} tags, ${summary.explicitEdgeCount} connections."

    private fun backupErrorMessage(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: "The backup operation could not be completed."

    class Factory(
        private val repository: LinkNestRepository,
        private val metadataFetcher: PageMetadataFetcher = DefaultPageMetadataFetcher(),
        private val backupService: LinkNestBackupService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repository, metadataFetcher, backupService) as T
    }
}
