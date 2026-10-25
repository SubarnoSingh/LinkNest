package com.linknest.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linknest.app.domain.Bookmark
import com.linknest.app.domain.Note
import com.linknest.app.domain.ResourceType
import com.linknest.app.presentation.MetadataFetchState
import com.linknest.app.presentation.MetadataUiState
import com.linknest.app.ui.theme.AccentBlue
import com.linknest.app.ui.theme.AccentGreen
import com.linknest.app.ui.theme.AccentOrange
import com.linknest.app.ui.theme.Border
import com.linknest.app.ui.theme.Purple
import com.linknest.app.ui.theme.PurpleBright
import com.linknest.app.ui.theme.SurfaceElevated
import com.linknest.app.ui.theme.TextMuted
import com.linknest.app.ui.theme.TextSecondary

@Composable
fun SectionTitle(
    title: String,
    eyebrow: String? = null,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            if (eyebrow != null) {
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Purple)
                Spacer(Modifier.height(4.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        action?.invoke()
    }
}

@Composable
fun FilterChipRow(
    labels: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEach { label ->
            AssistChip(
                onClick = { onSelect(label) },
                label = { Text(label) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (label == selected) Purple.copy(alpha = 0.18f) else Color.Transparent,
                    labelColor = if (label == selected) Purple else TextSecondary
                ),
                border = BorderStroke(1.dp, if (label == selected) Purple.copy(alpha = 0.7f) else Border)
            )
        }
    }
}

@Composable
fun TagPills(tags: List<String>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.take(5).forEach { tag ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Purple.copy(alpha = 0.11f),
                contentColor = PurpleBright
            ) {
                Text("#$tag", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            }
        }
    }
}

@Composable
fun ResourceTypeIcon(type: ResourceType, modifier: Modifier = Modifier) {
    val icon = when (type) {
        ResourceType.ARTICLE -> Icons.Outlined.Article
        ResourceType.VIDEO -> Icons.Outlined.Bookmark
        ResourceType.REPOSITORY -> Icons.Outlined.Code
        ResourceType.OTHER -> Icons.Outlined.Link
    }
    val tint = when (type) {
        ResourceType.ARTICLE -> AccentBlue
        ResourceType.VIDEO -> AccentOrange
        ResourceType.REPOSITORY -> AccentGreen
        ResourceType.OTHER -> Purple
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = type.label, tint = tint)
    }
}

@Composable
fun BookmarkCard(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    metadataState: MetadataUiState = MetadataUiState(),
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        border = BorderStroke(1.dp, Border.copy(alpha = 0.7f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                ResourceTypeIcon(bookmark.resourceType)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(bookmark.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bookmark.domain, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (metadataState.state == MetadataFetchState.LOADING) {
                            Spacer(Modifier.width(7.dp))
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = Purple)
                        }
                    }
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (bookmark.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (bookmark.isFavorite) "Remove favorite" else "Add favorite",
                        tint = if (bookmark.isFavorite) Color(0xFFFF9CB9) else TextMuted
                    )
                }
            }
            if (bookmark.description.isNotBlank()) {
                Text(bookmark.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            TagPills(bookmark.tags)
        }
    }
}

@Composable
fun NoteCard(note: Note, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        border = BorderStroke(1.dp, Border.copy(alpha = 0.7f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Purple.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Notes, contentDescription = null, tint = Purple) }
                Spacer(Modifier.width(12.dp))
                Text(note.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (note.body.isNotBlank()) {
                Text(note.body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            TagPills(note.tags)
        }
    }
}

@Composable
fun EmptyState(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icon()
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
