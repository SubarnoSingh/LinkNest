package com.linknest.app.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.linknest.app.domain.GraphBuilder
import com.linknest.app.domain.GraphEdgeType
import com.linknest.app.domain.GraphNode
import com.linknest.app.domain.GraphNodeType
import com.linknest.app.domain.GraphPoint
import com.linknest.app.domain.NodeKey
import com.linknest.app.presentation.GraphFilter
import com.linknest.app.presentation.MainUiState
import com.linknest.app.presentation.MainViewModel
import com.linknest.app.ui.components.EmptyState
import com.linknest.app.ui.components.FilterChipRow
import com.linknest.app.ui.theme.AccentBlue
import com.linknest.app.ui.theme.Border
import com.linknest.app.ui.theme.Purple
import com.linknest.app.ui.theme.Surface
import com.linknest.app.ui.theme.SurfaceElevated
import com.linknest.app.ui.theme.TextSecondary

@Composable
fun GraphScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    contentPadding: PaddingValues,
    onOpenBookmark: (com.linknest.app.domain.Bookmark) -> Unit,
    onOpenNote: (com.linknest.app.domain.Note) -> Unit,
    onConnect: (String) -> Unit
) {
    val snapshot = viewModel.graphSnapshot(state)
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val positions = remember(snapshot.nodes) {
        snapshot.nodes.mapIndexed { index, node -> node.key to GraphBuilder.stablePosition(index) }.toMap()
    }
    val selectedNode = snapshot.nodes.firstOrNull { it.key == selectedKey }
    val latestZoom by rememberUpdatedState(zoom)
    val latestPan by rememberUpdatedState(pan)

    fun fitGraph() {
        if (canvasSize.width <= 0 || canvasSize.height <= 0 || positions.isEmpty()) {
            zoom = 1f
            pan = Offset.Zero
            return
        }
        val minX = positions.values.minOf { it.x }
        val maxX = positions.values.maxOf { it.x }
        val minY = positions.values.minOf { it.y }
        val maxY = positions.values.maxOf { it.y }
        val graphWidth = (maxX - minX).coerceAtLeast(1f)
        val graphHeight = (maxY - minY).coerceAtLeast(1f)
        val availableWidth = (canvasSize.width - 160f).coerceAtLeast(1f)
        val availableHeight = (canvasSize.height - 260f).coerceAtLeast(1f)
        val fittedZoom = minOf(availableWidth / graphWidth, availableHeight / graphHeight)
            .coerceIn(0.55f, 2.8f)
        val graphCenter = Offset((minX + maxX) / 2f, (minY + maxY) / 2f)
        zoom = fittedZoom
        pan = Offset(-graphCenter.x * fittedZoom, -graphCenter.y * fittedZoom)
    }

    LaunchedEffect(snapshot.nodes) {
        if (selectedKey != null && snapshot.nodes.none { it.key == selectedKey }) selectedKey = null
    }

    Box(Modifier.fillMaxSize().padding(contentPadding).background(Surface)) {
        if (snapshot.nodes.isEmpty()) {
            EmptyState(
                modifier = Modifier.align(Alignment.Center),
                icon = { Icon(Icons.Outlined.CenterFocusStrong, contentDescription = null, tint = Purple, modifier = Modifier.size(42.dp)) },
                title = "Your graph starts here",
                body = "Save a few links or notes and LinkNest will reveal shared tags. Add explicit connections when two ideas belong together.",
                actionLabel = null,
                onAction = null
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(snapshot.nodes, canvasSize) {
                        detectTapGestures { tap ->
                            val local = Offset(
                                (tap.x - canvasSize.width / 2f - latestPan.x) / latestZoom,
                                (tap.y - canvasSize.height / 2f - latestPan.y) / latestZoom
                            )
                            selectedKey = positions.entries
                                .map { (key, position) -> key to distance(local, Offset(position.x, position.y)) }
                                .filter { it.second <= 100f }
                                .minByOrNull { it.second }
                                ?.first
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, gesturePan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(0.55f, 2.8f)
                            pan += gesturePan
                        }
                    }
            ) {
                drawGraph(snapshot, positions, zoom, pan, selectedKey)
            }
        }

        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Knowledge graph", style = MaterialTheme.typography.headlineSmall)
                    Text("${snapshot.nodes.size} nodes · ${snapshot.edges.size} connections", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = ::fitGraph) { Icon(Icons.Outlined.CenterFocusStrong, contentDescription = "Fit graph") }
                IconButton(onClick = viewModel::toggleTagEdges) { Icon(Icons.Outlined.Tune, contentDescription = "Toggle tag edges", tint = if (state.showTagEdges) Purple else TextSecondary) }
            }
            FilterChipRow(
                labels = GraphFilter.entries.map { it.label },
                selected = state.graphFilter.label,
                onSelect = { label -> viewModel.setGraphFilter(GraphFilter.entries.first { it.label == label }) }
            )
            val graphTags = state.content.bookmarks.flatMap { it.tags } + state.content.notes.flatMap { it.tags }
            if (graphTags.isNotEmpty()) {
                FilterChipRow(
                    labels = listOf("All tags") + graphTags.distinct().sorted().take(10).map { "#$it" },
                    selected = state.graphTag?.let { "#$it" } ?: "All tags",
                    onSelect = { label -> viewModel.setGraphTag(label.takeIf { it != "All tags" }?.removePrefix("#")) }
                )
            }
        }

        selectedNode?.let { node ->
            SelectedNodePanel(
                node = node,
                state = state,
                visibleNodeKeys = snapshot.nodes.map { it.key }.toSet(),
                viewModel = viewModel,
                modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp),
                onClose = { selectedKey = null },
                onOpen = {
                    node.bookmark?.let(onOpenBookmark)
                    node.note?.let(onOpenNote)
                },
                onConnect = { onConnect(node.key) }
            )
        }
    }
}

private fun DrawScope.drawGraph(
    snapshot: com.linknest.app.domain.GraphSnapshot,
    positions: Map<String, GraphPoint>,
    zoom: Float,
    pan: Offset,
    selectedKey: String?
) {
    drawRect(color = Surface)
    withTransform({
        translate(size.width / 2f + pan.x, size.height / 2f + pan.y)
        scale(zoom, zoom)
    }) {
        snapshot.edges.forEach { edge ->
            val source = positions[edge.source] ?: return@forEach
            val target = positions[edge.target] ?: return@forEach
            drawLine(
                color = if (edge.type == GraphEdgeType.EXPLICIT) Purple.copy(alpha = 0.72f) else Purple.copy(alpha = 0.24f),
                start = Offset(source.x, source.y),
                end = Offset(target.x, target.y),
                strokeWidth = if (edge.type == GraphEdgeType.EXPLICIT) 2.2f else 1.2f
            )
        }
        snapshot.nodes.forEach { node ->
            val position = positions[node.key] ?: return@forEach
            val isSelected = node.key == selectedKey
            val nodeColor = if (node.nodeType == GraphNodeType.NOTE) AccentBlue else Purple
            drawCircle(
                color = nodeColor.copy(alpha = if (isSelected) 0.28f else 0.16f),
                radius = if (isSelected) 66f else 54f,
                center = Offset(position.x, position.y)
            )
            drawCircle(
                color = nodeColor,
                radius = if (isSelected) 32f else 27f,
                center = Offset(position.x, position.y)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = 6f,
                center = Offset(position.x - 3f, position.y - 3f)
            )
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 25f
                    alpha = 225
                    isAntiAlias = true
                }
                val label = node.title.take(23)
                canvas.nativeCanvas.drawText(label, position.x - (paint.measureText(label) / 2f), position.y + 86f, paint)
            }
        }
    }
}

@Composable
private fun SelectedNodePanel(
    node: GraphNode,
    state: MainUiState,
    visibleNodeKeys: Set<String>,
    viewModel: MainViewModel,
    modifier: Modifier,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    onConnect: () -> Unit
) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceElevated), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(node.title, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    Text(node.subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
            }
            if (node.tags.isNotEmpty()) Text(node.tags.joinToString("  ") { "#$it" }, color = Purple, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onConnect, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Link, contentDescription = null); Spacer(Modifier.width(5.dp)); Text("Connect") }
                if (node.bookmark != null || node.note != null) {
                    OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.OpenInNew, contentDescription = null); Spacer(Modifier.width(5.dp)); Text("Open") }
                }
            }
            val explicitConnections = state.content.explicitEdges.filter { edge ->
                (edge.source == node.key || edge.target == node.key) &&
                    (if (edge.source == node.key) edge.target else edge.source) in visibleNodeKeys
            }
            if (explicitConnections.isNotEmpty()) {
                Text("Explicit connections", style = MaterialTheme.typography.labelLarge)
                explicitConnections.take(3).forEach { edge ->
                    val other = if (edge.source == node.key) edge.target else edge.source
                    val otherTitle = state.content.bookmarks.firstOrNull { NodeKey.bookmark(it.id) == other }?.title
                        ?: state.content.notes.firstOrNull { NodeKey.note(it.id) == other }?.title
                        ?: other
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(otherTitle, color = TextSecondary, modifier = Modifier.weight(1f), maxLines = 1)
                        TextButton(onClick = { viewModel.removeEdge(node.key, other) }) { Text("Remove") }
                    }
                }
            }
        }
    }
}

private fun distance(first: Offset, second: Offset): Float {
    val dx = first.x - second.x
    val dy = first.y - second.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
