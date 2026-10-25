package com.linknest.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linknest.app.data.repository.BookmarkDraft
import com.linknest.app.data.repository.SaveBookmarkResult
import com.linknest.app.domain.Bookmark
import com.linknest.app.domain.LibraryItem
import com.linknest.app.domain.Note
import com.linknest.app.domain.ResourceType
import com.linknest.app.domain.tagsAsText
import com.linknest.app.presentation.LibraryFilter
import com.linknest.app.presentation.LibrarySort
import com.linknest.app.presentation.MainUiState
import com.linknest.app.presentation.MainViewModel
import com.linknest.app.ui.components.BookmarkCard
import com.linknest.app.ui.components.EmptyState
import com.linknest.app.ui.components.FilterChipRow
import com.linknest.app.ui.components.NoteCard
import com.linknest.app.ui.components.SectionTitle
import com.linknest.app.ui.theme.Border
import com.linknest.app.ui.theme.Purple
import com.linknest.app.ui.theme.Surface
import com.linknest.app.ui.theme.TextSecondary

@Composable
fun LibraryScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onOpenBookmark: (Bookmark) -> Unit,
    onOpenNote: (Note) -> Unit,
    onBackupRestore: () -> Unit
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val items = viewModel.visibleLibraryItems(state)
    val hasAnyContent = state.content.bookmarks.isNotEmpty() || state.content.notes.isNotEmpty()

    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Library", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        if (hasAnyContent) "Your saved ideas, in one place." else "Capture the things worth returning to.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = { searchOpen = !searchOpen }) {
                    Icon(if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search, contentDescription = "Search library")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Library options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Backup & restore") },
                            onClick = {
                                menuOpen = false
                                onBackupRestore()
                            }
                        )
                    }
                }
            }
            if (searchOpen) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.libraryQuery,
                    onValueChange = viewModel::setLibraryQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search titles, notes, URLs, tags…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = Border)
                )
            }
            Spacer(Modifier.height(18.dp))
            FilterChipRow(
                labels = LibraryFilter.entries.map { it.label },
                selected = state.libraryFilter.label,
                onSelect = { label -> viewModel.setLibraryFilter(LibraryFilter.entries.first { it.label == label }) }
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${items.size} ${if (items.size == 1) "item" else "items"}", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                SortMenu(state.librarySort) { viewModel.setLibrarySort(it) }
            }
            Spacer(Modifier.height(8.dp))

            if (items.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, tint = Purple, modifier = Modifier.padding(10.dp)) },
                    title = if (hasAnyContent) "No matches" else "Your library is quiet",
                    body = if (hasAnyContent) "Try a different search or filter." else "Save a link from another app or add your first resource below.",
                    actionLabel = if (hasAnyContent) null else "Add a link",
                    onAction = if (hasAnyContent) null else onAdd
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.key }) { item ->
                        when (item) {
                            is LibraryItem.BookmarkItem -> BookmarkCard(
                                bookmark = item.bookmark,
                                onClick = { onOpenBookmark(item.bookmark) },
                                onFavorite = { viewModel.toggleFavorite(item.bookmark.id) },
                                metadataState = state.metadataStates[item.bookmark.id] ?: com.linknest.app.presentation.MetadataUiState()
                            )
                            is LibraryItem.NoteItem -> NoteCard(item.note, onClick = { onOpenNote(item.note) })
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Icon(Icons.Outlined.Add, contentDescription = "Add link") }
    }
}

@Composable
private fun SortMenu(selected: LibrarySort, onSelected: (LibrarySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.FilterList, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(selected.label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibrarySort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    onClick = { onSelected(sort); expanded = false }
                )
            }
        }
    }
}
