package com.linknest.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.linknest.app.data.repository.BookmarkDraft
import com.linknest.app.data.repository.NoteDraft
import com.linknest.app.domain.Bookmark
import com.linknest.app.domain.Note
import com.linknest.app.domain.tagsAsText
import com.linknest.app.presentation.MainUiState
import com.linknest.app.presentation.MetadataFetchState
import com.linknest.app.presentation.MetadataUiState
import com.linknest.app.ui.components.ResourceTypeIcon
import com.linknest.app.ui.components.TagPills
import com.linknest.app.ui.theme.Border
import com.linknest.app.ui.theme.Purple
import com.linknest.app.ui.theme.SurfaceElevated
import com.linknest.app.ui.theme.TextSecondary

@Composable
fun BookmarkEditorDialog(
    initial: Bookmark?,
    sharedUrl: String?,
    sharedTitle: String?,
    sharedNote: String?,
    onDismiss: () -> Unit,
    onSave: (BookmarkDraft, (String?) -> Unit) -> Unit
) {
    var url by rememberSaveable(initial?.id, sharedUrl) { mutableStateOf(initial?.originalUrl ?: sharedUrl.orEmpty()) }
    var title by rememberSaveable(initial?.id, sharedTitle) { mutableStateOf(initial?.title ?: sharedTitle.orEmpty()) }
    var description by rememberSaveable(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var note by rememberSaveable(initial?.id, sharedNote) { mutableStateOf(initial?.userNote ?: sharedNote.orEmpty()) }
    var tags by rememberSaveable(initial?.id) { mutableStateOf(initial?.tags?.let(::tagsAsText).orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(18.dp).heightIn(max = 720.dp),
            shape = MaterialTheme.shapes.large,
            color = SurfaceElevated
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Text(if (initial == null) "Save a link" else "Edit link", style = MaterialTheme.typography.headlineSmall)
                Text("Everything stays on this device. Add context now, refine it later.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(url, { url = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("URL") }, singleLine = true, isError = error != null, enabled = !saving)
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true, enabled = !saving)
                OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Description (optional)") }, minLines = 2, maxLines = 4, enabled = !saving)
                OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Personal note (optional)") }, minLines = 3, maxLines = 6, enabled = !saving)
                OutlinedTextField(tags, { tags = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Tags") }, placeholder = { Text("design, kotlin, read-later") }, singleLine = true, enabled = !saving)
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (!saving) {
                                saving = true
                                onSave(BookmarkDraft(initial?.id, url, title, description, note, tags)) { message ->
                                    saving = false
                                    error = message
                                }
                            }
                        },
                        enabled = !saving
                    ) { Text(if (saving) "Saving…" else "Save") }
                }
            }
        }
    }
}

@Composable
fun NoteEditorDialog(
    initial: Note?,
    onDismiss: () -> Unit,
    onSave: (NoteDraft, (String?) -> Unit) -> Unit
) {
    val editingId = initial?.id?.takeIf { it > 0L }
    var title by rememberSaveable(initial?.id, initial?.title) { mutableStateOf(initial?.title.orEmpty()) }
    var body by rememberSaveable(initial?.id, initial?.body) { mutableStateOf(initial?.body.orEmpty()) }
    var tags by rememberSaveable(initial?.id, initial?.tags) { mutableStateOf(initial?.tags?.let(::tagsAsText).orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(18.dp).heightIn(max = 720.dp),
            shape = MaterialTheme.shapes.large,
            color = SurfaceElevated
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text(if (editingId == null) "New note" else "Edit note", style = MaterialTheme.typography.headlineSmall)
                Text("Notes are searchable and can be connected to any saved item.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true, enabled = !saving)
                OutlinedTextField(body, { body = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp), label = { Text("Write something…") }, minLines = 7, enabled = !saving)
                OutlinedTextField(tags, { tags = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Tags") }, placeholder = { Text("idea, project, reference") }, singleLine = true, enabled = !saving)
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (!saving) {
                                saving = true
                                onSave(NoteDraft(editingId, title, body, tags)) { message ->
                                    saving = false
                                    error = message
                                }
                            }
                        },
                        enabled = !saving
                    ) { Text(if (saving) "Saving…" else "Save note") }
                }
            }
        }
    }
}

@Composable
fun BookmarkDetailDialog(
    bookmark: Bookmark,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFavorite: () -> Unit,
    onConnect: () -> Unit,
    onOpen: () -> Unit,
    metadataState: MetadataUiState = MetadataUiState(),
    onRetryMetadata: () -> Unit = {}
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(18.dp).heightIn(max = 720.dp), shape = MaterialTheme.shapes.large, color = SurfaceElevated) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    ResourceTypeIcon(bookmark.resourceType)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(bookmark.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(bookmark.domain, color = TextSecondary)
                    }
                    IconButton(onClick = onFavorite) { Icon(if (bookmark.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "Favorite", tint = if (bookmark.isFavorite) Color(0xFFFF9CB9) else TextSecondary) }
                }
                when (metadataState.state) {
                    MetadataFetchState.LOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = Purple)
                        Spacer(Modifier.width(8.dp))
                        Text("Fetching page details…", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    MetadataFetchState.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Page details unavailable", color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onRetryMetadata) { Text("Retry") }
                    }
                    else -> Unit
                }
                HorizontalDivider(color = Border)
                Text(bookmark.originalUrl, color = Purple, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (bookmark.description.isNotBlank()) Text(bookmark.description, color = TextSecondary)
                bookmark.canonicalUrl?.takeIf { it != bookmark.url }?.let { canonical ->
                    Text("Canonical: $canonical", color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (bookmark.userNote.isNotBlank()) {
                    Text("Your note", style = MaterialTheme.typography.labelLarge)
                    Text(bookmark.userNote, color = TextSecondary)
                }
                TagPills(bookmark.tags)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.OpenInNew, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Open") }
                    OutlinedButton(onClick = onConnect, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Link, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Connect") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Edit") }
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete this link?") }, text = { Text("This also removes its connections and tag associations.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    }
}

@Composable
fun NoteDetailDialog(
    note: Note,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConnect: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(18.dp).heightIn(max = 720.dp), shape = MaterialTheme.shapes.large, color = SurfaceElevated) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Notes, contentDescription = null, tint = Purple)
                    Spacer(Modifier.width(12.dp))
                    Text(note.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = Border)
                Text(note.body.ifBlank { "No body yet." }, color = TextSecondary)
                TagPills(note.tags)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onConnect, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Link, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Connect") }
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Edit, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Edit") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete this note?") }, text = { Text("This also removes its connections and tag associations.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    }
}

@Composable
fun NodeConnectionDialog(
    sourceKey: String,
    state: MainUiState,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit
) {
    val candidates = buildList {
        state.content.bookmarks.forEach { bookmark -> add("bookmark:${bookmark.id}" to bookmark.title) }
        state.content.notes.forEach { note -> add("note:${note.id}" to note.title) }
    }.filterNot { it.first == sourceKey }
    var query by remember { mutableStateOf("") }
    val visible = candidates.filter { it.second.contains(query, true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect to…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Create a permanent related-to connection.", color = TextSecondary)
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search items") }, singleLine = true)
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    if (visible.isEmpty()) Text("No other items yet.", color = TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
                    visible.forEach { (key, title) ->
                        TextButton(onClick = { onConnect(key) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(if (key.startsWith("note:")) Icons.Outlined.Notes else Icons.Outlined.BookmarkBorder, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
