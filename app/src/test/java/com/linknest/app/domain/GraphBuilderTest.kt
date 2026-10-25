package com.linknest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphBuilderTest {
    private val bookmark = Bookmark(
        id = 1,
        url = "https://example.com/a",
        originalUrl = "https://example.com/a",
        title = "A",
        description = "",
        userNote = "",
        domain = "example.com",
        resourceType = ResourceType.ARTICLE,
        createdAt = 1,
        updatedAt = 1,
        isFavorite = false,
        tags = listOf("kotlin", "android")
    )

    @Test
    fun createsLimitedSharedTagEdgesWithoutMutatingExplicitEdges() {
        val second = bookmark.copy(id = 2, title = "B", tags = listOf("kotlin"))
        val explicit = GraphEdge(NodeKey.bookmark(1), NodeKey.bookmark(2), GraphEdgeType.EXPLICIT)

        val graph = GraphBuilder.build(listOf(bookmark, second), emptyList(), listOf(explicit))

        assertEquals(2, graph.nodes.size)
        assertEquals(1, graph.edges.size)
        assertEquals(GraphEdgeType.EXPLICIT, graph.edges.single().type)
    }

    @Test
    fun generatesMeaningfulSharedTagRelationshipWhenNoExplicitEdgeExists() {
        val second = bookmark.copy(id = 2, title = "B", tags = listOf("kotlin"))

        val graph = GraphBuilder.build(listOf(bookmark, second), emptyList(), emptyList())

        assertEquals(1, graph.edges.size)
        assertEquals(GraphEdgeType.SHARED_TAG, graph.edges.single().type)
        assertEquals("kotlin", graph.edges.single().label)
    }

    @Test
    fun stablePositionsAreDeterministicAndDoNotCollapse() {
        assertEquals(GraphBuilder.stablePosition(3), GraphBuilder.stablePosition(3))
        assertNotEquals(GraphBuilder.stablePosition(1), GraphBuilder.stablePosition(2))
        assertTrue(GraphBuilder.edgeKey("note:2", "bookmark:1") == GraphBuilder.edgeKey("bookmark:1", "note:2"))
        assertFalse(GraphBuilder.edgeKey("note:2", "bookmark:1").isBlank())
    }

    @Test
    fun ignoresExplicitEdgesForDeletedNodes() {
        val staleEdge = GraphEdge(NodeKey.bookmark(1), NodeKey.note(404), GraphEdgeType.EXPLICIT)

        val graph = GraphBuilder.build(listOf(bookmark), emptyList(), listOf(staleEdge))

        assertTrue(graph.edges.isEmpty())
    }
}
