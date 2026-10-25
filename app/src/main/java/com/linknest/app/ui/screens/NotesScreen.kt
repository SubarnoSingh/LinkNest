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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.linknest.app.domain.Note
import com.linknest.app.presentation.MainUiState
import com.linknest.app.presentation.MainViewModel
import com.linknest.app.ui.components.EmptyState
import com.linknest.app.ui.components.NoteCard
import com.linknest.app.ui.theme.Border
import com.linknest.app.ui.theme.Purple
import com.linknest.app.ui.theme.TextSecondary

@Composable
fun NotesScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onOpenNote: (Note) -> Unit
) {
    val notes = viewModel.visibleNotes(state)
    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Notes", style = MaterialTheme.typography.headlineLarge)
                    Text("Keep the context behind your links.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, contentDescription = "New note") }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.notesQuery,
                onValueChange = viewModel::setNotesQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search notes and tags…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = Border)
            )
            Spacer(Modifier.height(16.dp))
            if (notes.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.Notes, contentDescription = null, tint = Purple, modifier = Modifier.padding(10.dp)) },
                    title = if (state.content.notes.isEmpty()) "Start a note" else "No notes found",
                    body = if (state.content.notes.isEmpty()) "Write down an idea, a decision, or the context behind a saved resource." else "Try a different search.",
                    actionLabel = if (state.content.notes.isEmpty()) "Create note" else null,
                    onAction = if (state.content.notes.isEmpty()) onAdd else null
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notes, key = { it.id }) { note -> NoteCard(note = note, onClick = { onOpenNote(note) }) }
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Icon(Icons.Outlined.Add, contentDescription = "New note") }
    }
}
