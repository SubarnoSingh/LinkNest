package com.linknest.app.domain

object GraphBuilder {
    fun build(bookmarks: List<Bookmark>, notes: List<Note>, explicitEdges: List<GraphEdge>): GraphSnapshot {
        val nodes = buildList {
            bookmarks.sortedBy { it.id }.forEach { bookmark ->
                add(
                    GraphNode(
                        key = NodeKey.bookmark(bookmark.id),
                        title = bookmark.title,
                        nodeType = GraphNodeType.BOOKMARK,
                        subtitle = bookmark.domain,
                        tags = bookmark.tags,
                        bookmark = bookmark
                    )
                )
            }
            notes.sortedBy { it.id }.forEach { note ->
                add(
                    GraphNode(
                        key = NodeKey.note(note.id),
                        title = note.title,
                        nodeType = GraphNodeType.NOTE,
                        subtitle = "Personal note",
                        tags = note.tags,
                        note = note
                    )
                )
            }
        }

        val edges = linkedMapOf<String, GraphEdge>()
        val nodeKeys = nodes.map { it.key }.toSet()
        explicitEdges.forEach { edge ->
            if (edge.source != edge.target && edge.source in nodeKeys && edge.target in nodeKeys) {
                val key = edgeKey(edge.source, edge.target)
                edges[key] = edge.copy(type = GraphEdgeType.EXPLICIT)
            }
        }

        val nodesByTag = nodes.flatMap { node -> node.tags.distinct().map { it to node } }
            .groupBy({ it.first }, { it.second })
        nodesByTag.toSortedMap().forEach { (tag, taggedNodes) ->
            val sorted = taggedNodes.distinctBy { it.key }.sortedBy { it.key }
            // Adjacent pairs keep popular tags useful without turning the graph into a hairball.
            sorted.zipWithNext().take(4).forEach { (left, right) ->
                val key = edgeKey(left.key, right.key)
                if (key !in edges) {
                    edges[key] = GraphEdge(
                        source = left.key,
                        target = right.key,
                        type = GraphEdgeType.SHARED_TAG,
                        label = tag
                    )
                }
            }
        }

        return GraphSnapshot(nodes = nodes, edges = edges.values.toList())
    }

    fun edgeKey(first: String, second: String): String =
        if (first < second) "$first|$second" else "$second|$first"

    fun stablePosition(index: Int): GraphPoint {
        if (index == 0) return GraphPoint(0f, 0f)
        val goldenAngle = 2.3999631f
        val radius = 150f + (index / 2f) * 48f
        val angle = index * goldenAngle
        return GraphPoint(
            x = kotlin.math.cos(angle) * radius,
            y = kotlin.math.sin(angle) * radius
        )
    }
}

data class GraphPoint(val x: Float, val y: Float)
