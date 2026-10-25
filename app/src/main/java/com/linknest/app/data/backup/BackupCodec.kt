package com.linknest.app.data.backup

import com.linknest.app.data.local.BookmarkEntity
import com.linknest.app.data.local.BookmarkTagCrossRef
import com.linknest.app.data.local.GraphEdgeEntity
import com.linknest.app.data.local.NoteEntity
import com.linknest.app.data.local.NoteTagCrossRef
import com.linknest.app.data.local.TagEntity
import com.linknest.app.domain.ResourceType
import com.linknest.app.domain.UrlUtils
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

object LinkNestBackupCodec {
    private const val FORMAT = "format"
    private const val VERSION = "version"
    private const val EXPORTED_AT = "exportedAt"
    private const val BOOKMARKS = "bookmarks"
    private const val NOTES = "notes"
    private const val TAGS = "tags"
    private const val BOOKMARK_TAGS = "bookmarkTags"
    private const val NOTE_TAGS = "noteTags"
    private const val GRAPH_EDGES = "graphEdges"

    fun encode(snapshot: LinkNestBackupSnapshot, exportedAt: Long = System.currentTimeMillis()): String {
        if (exportedAt < 0L) throw BackupValidationException("The backup timestamp is invalid.")
        val root = JSONObject()
            .put(FORMAT, LinkNestBackupFormat.FORMAT_IDENTIFIER)
            .put(VERSION, LinkNestBackupFormat.VERSION)
            .put(EXPORTED_AT, exportedAt)
            .put(BOOKMARKS, JSONArray())
            .put(NOTES, JSONArray())
            .put(TAGS, JSONArray())
            .put(BOOKMARK_TAGS, JSONArray())
            .put(NOTE_TAGS, JSONArray())
            .put(GRAPH_EDGES, JSONArray())

        snapshot.bookmarks.sortedBy { it.id }.forEach { bookmark ->
            root.getJSONArray(BOOKMARKS).put(bookmarkObject(bookmark))
        }
        snapshot.notes.sortedBy { it.id }.forEach { note ->
            root.getJSONArray(NOTES).put(noteObject(note))
        }
        snapshot.tags.sortedBy { it.id }.forEach { tag ->
            root.getJSONArray(TAGS).put(tagObject(tag))
        }
        snapshot.bookmarkTags.sortedWith(compareBy<BookmarkTagCrossRef> { it.bookmarkId }.thenBy { it.tagId }).forEach { ref ->
            root.getJSONArray(BOOKMARK_TAGS).put(bookmarkTagObject(ref))
        }
        snapshot.noteTags.sortedWith(compareBy<NoteTagCrossRef> { it.noteId }.thenBy { it.tagId }).forEach { ref ->
            root.getJSONArray(NOTE_TAGS).put(noteTagObject(ref))
        }
        snapshot.graphEdges.sortedBy { it.id }.forEach { edge ->
            root.getJSONArray(GRAPH_EDGES).put(graphEdgeObject(edge))
        }

        val json = root.toString(2)
        if (json.toByteArray(Charsets.UTF_8).size > LinkNestBackupFormat.MAX_BACKUP_BYTES) {
            throw BackupValidationException("This library is too large to export as one backup file.")
        }
        return json
    }

    fun parse(input: InputStream): ParsedLinkNestBackup = parse(readBounded(input))

    fun parse(bytes: ByteArray): ParsedLinkNestBackup {
        if (bytes.size > LinkNestBackupFormat.MAX_BACKUP_BYTES) {
            throw BackupValidationException("The backup file is too large.")
        }
        val text = decodeUtf8(bytes)
        val root = parseRoot(text)
        requireKeys(
            root,
            setOf(FORMAT, VERSION, EXPORTED_AT, BOOKMARKS, NOTES, TAGS, BOOKMARK_TAGS, NOTE_TAGS, GRAPH_EDGES),
            "Backup"
        )
        val format = requiredString(root, FORMAT, 128, "Backup")
        if (format != LinkNestBackupFormat.FORMAT_IDENTIFIER) {
            throw BackupValidationException("This file is not a LinkNest backup.")
        }
        val version = requiredLong(root, VERSION, "Backup")
        if (version != LinkNestBackupFormat.VERSION.toLong()) {
            throw BackupValidationException("This backup version is not supported.")
        }
        val exportedAt = requiredNonNegativeLong(root, EXPORTED_AT, "Backup")

        val bookmarks = parseBookmarks(requiredArray(root, BOOKMARKS, "Backup"))
        val notes = parseNotes(requiredArray(root, NOTES, "Backup"))
        val tags = parseTags(requiredArray(root, TAGS, "Backup"))
        val bookmarkTags = parseBookmarkTags(requiredArray(root, BOOKMARK_TAGS, "Backup"))
        val noteTags = parseNoteTags(requiredArray(root, NOTE_TAGS, "Backup"))
        val graphEdges = parseGraphEdges(requiredArray(root, GRAPH_EDGES, "Backup"))

        validateRelationships(bookmarks, notes, tags, bookmarkTags, noteTags, graphEdges)
        return ParsedLinkNestBackup(
            exportedAt = exportedAt,
            snapshot = LinkNestBackupSnapshot(bookmarks, notes, tags, bookmarkTags, noteTags, graphEdges)
        )
    }

    private fun bookmarkObject(bookmark: BookmarkEntity): JSONObject = JSONObject()
        .put("id", bookmark.id)
        .put("url", bookmark.url)
        .put("originalUrl", bookmark.originalUrl)
        .put("title", bookmark.title)
        .put("description", bookmark.description)
        .put("userNote", bookmark.userNote)
        .put("domain", bookmark.domain)
        .put("resourceType", bookmark.resourceType.name)
        .put("createdAt", bookmark.createdAt)
        .put("updatedAt", bookmark.updatedAt)
        .put("isFavorite", bookmark.isFavorite)
        .put("titleUserEdited", bookmark.titleUserEdited)
        .put("descriptionUserEdited", bookmark.descriptionUserEdited)
        .put("canonicalUrl", bookmark.canonicalUrl ?: JSONObject.NULL)
        .put("faviconUrl", bookmark.faviconUrl ?: JSONObject.NULL)

    private fun noteObject(note: NoteEntity): JSONObject = JSONObject()
        .put("id", note.id)
        .put("title", note.title)
        .put("body", note.body)
        .put("createdAt", note.createdAt)
        .put("updatedAt", note.updatedAt)

    private fun tagObject(tag: TagEntity): JSONObject = JSONObject()
        .put("id", tag.id)
        .put("name", tag.name)

    private fun bookmarkTagObject(ref: BookmarkTagCrossRef): JSONObject = JSONObject()
        .put("bookmarkId", ref.bookmarkId)
        .put("tagId", ref.tagId)

    private fun noteTagObject(ref: NoteTagCrossRef): JSONObject = JSONObject()
        .put("noteId", ref.noteId)
        .put("tagId", ref.tagId)

    private fun graphEdgeObject(edge: GraphEdgeEntity): JSONObject = JSONObject()
        .put("id", edge.id)
        .put("sourceNodeKey", edge.sourceNodeKey)
        .put("targetNodeKey", edge.targetNodeKey)
        .put("relationshipType", edge.relationshipType)
        .put("createdAt", edge.createdAt)

    private fun parseRoot(text: String): JSONObject {
        val tokener = JSONTokener(text)
        val value = runCatching { tokener.nextValue() }.getOrElse {
            throw BackupValidationException("The backup file is not valid JSON.")
        }
        if (value !is JSONObject) throw BackupValidationException("The backup root must be a JSON object.")
        val trailing = runCatching { tokener.nextClean() }.getOrElse {
            throw BackupValidationException("The backup file contains invalid trailing data.")
        }
        if (trailing != 0.toChar()) throw BackupValidationException("The backup file contains trailing data.")
        return value
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream(minOf(LinkNestBackupFormat.MAX_BACKUP_BYTES, 16 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            if (count > LinkNestBackupFormat.MAX_BACKUP_BYTES - total) {
                throw BackupValidationException("The backup file is too large.")
            }
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray()
    }

    private fun decodeUtf8(bytes: ByteArray): String = runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrElse {
        throw BackupValidationException("The backup file is not valid UTF-8 text.")
    }

    private fun requireKeys(value: JSONObject, allowed: Set<String>, context: String) {
        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in allowed) throw BackupValidationException("$context contains unsupported field '$key'.")
        }
    }

    private fun requiredArray(value: JSONObject, key: String, context: String): JSONArray {
        val raw = value.opt(key)
        if (raw !is JSONArray) throw BackupValidationException("$context.$key must be an array.")
        if (raw.length() > LinkNestBackupFormat.MAX_RECORD_COUNT) {
            throw BackupValidationException("$context.$key contains too many records.")
        }
        return raw
    }

    private fun requiredString(value: JSONObject, key: String, maxLength: Int, context: String): String {
        val raw = value.opt(key)
        if (raw !is String) throw BackupValidationException("$context.$key must be a string.")
        if (raw.length > maxLength) throw BackupValidationException("$context.$key is too long.")
        return raw
    }

    private fun requiredNullableString(value: JSONObject, key: String, maxLength: Int, context: String): String? {
        if (!value.has(key)) throw BackupValidationException("$context.$key is required.")
        val raw = value.opt(key)
        if (raw === JSONObject.NULL) return null
        if (raw !is String) throw BackupValidationException("$context.$key must be a string or null.")
        if (raw.length > maxLength) throw BackupValidationException("$context.$key is too long.")
        return raw
    }

    private fun requiredBoolean(value: JSONObject, key: String, context: String): Boolean {
        val raw = value.opt(key)
        if (raw !is Boolean) throw BackupValidationException("$context.$key must be a boolean.")
        return raw
    }

    private fun requiredLong(value: JSONObject, key: String, context: String): Long {
        val raw = value.opt(key)
        if (raw !is Number || raw is Float || raw is Double && raw.toLong().toDouble() != raw) {
            throw BackupValidationException("$context.$key must be an integer.")
        }
        return raw.toLong()
    }

    private fun requiredPositiveLong(value: JSONObject, key: String, context: String): Long {
        val result = requiredLong(value, key, context)
        if (result <= 0L) throw BackupValidationException("$context.$key must be positive.")
        return result
    }

    private fun requiredNonNegativeLong(value: JSONObject, key: String, context: String): Long {
        val result = requiredLong(value, key, context)
        if (result < 0L) throw BackupValidationException("$context.$key cannot be negative.")
        return result
    }

    private fun objectAt(array: JSONArray, index: Int, context: String): JSONObject {
        val raw = array.opt(index)
        if (raw !is JSONObject) throw BackupValidationException("$context[$index] must be an object.")
        return raw
    }

    private fun parseBookmarks(array: JSONArray): List<BookmarkEntity> = buildList {
        val ids = mutableSetOf<Long>()
        val urls = mutableSetOf<String>()
        repeat(array.length()) { index ->
            val context = "bookmarks[$index]"
            val value = objectAt(array, index, "bookmarks")
            requireKeys(
                value,
                setOf(
                    "id", "url", "originalUrl", "title", "description", "userNote", "domain",
                    "resourceType", "createdAt", "updatedAt", "isFavorite", "titleUserEdited",
                    "descriptionUserEdited", "canonicalUrl", "faviconUrl"
                ),
                context
            )
            val id = requiredPositiveLong(value, "id", context)
            if (!ids.add(id)) throw BackupValidationException("$context.id is duplicated.")
            val url = requiredString(value, "url", LinkNestBackupFormat.MAX_URL_LENGTH, context)
            val normalized = UrlUtils.normalize(url)
                ?: throw BackupValidationException("$context.url is not a valid http or https URL.")
            if (normalized.canonical != url) throw BackupValidationException("$context.url is not normalized.")
            if (!urls.add(url)) throw BackupValidationException("$context.url is duplicated.")
            val originalUrl = requiredString(value, "originalUrl", LinkNestBackupFormat.MAX_URL_LENGTH, context)
            if (originalUrl.isBlank()) throw BackupValidationException("$context.originalUrl cannot be blank.")
            val title = requiredString(value, "title", LinkNestBackupFormat.MAX_TEXT_LENGTH, context)
            val description = requiredString(value, "description", LinkNestBackupFormat.MAX_TEXT_LENGTH, context)
            val userNote = requiredString(value, "userNote", LinkNestBackupFormat.MAX_TEXT_LENGTH, context)
            val domain = requiredString(value, "domain", LinkNestBackupFormat.MAX_URL_LENGTH, context)
            if (domain != normalized.domain) throw BackupValidationException("$context.domain does not match its URL.")
            val resourceTypeName = requiredString(value, "resourceType", 32, context)
            val resourceType = runCatching { ResourceType.valueOf(resourceTypeName) }.getOrElse {
                throw BackupValidationException("$context.resourceType is unsupported.")
            }
            val createdAt = requiredNonNegativeLong(value, "createdAt", context)
            val updatedAt = requiredNonNegativeLong(value, "updatedAt", context)
            val canonicalUrl = requiredNullableUrl(value, "canonicalUrl", context)
            val faviconUrl = requiredNullableUrl(value, "faviconUrl", context)
            add(
                BookmarkEntity(
                    id = id,
                    url = url,
                    originalUrl = originalUrl,
                    title = title,
                    description = description,
                    userNote = userNote,
                    domain = domain,
                    resourceType = resourceType,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    isFavorite = requiredBoolean(value, "isFavorite", context),
                    titleUserEdited = requiredBoolean(value, "titleUserEdited", context),
                    descriptionUserEdited = requiredBoolean(value, "descriptionUserEdited", context),
                    canonicalUrl = canonicalUrl,
                    faviconUrl = faviconUrl
                )
            )
        }
    }

    private fun parseNotes(array: JSONArray): List<NoteEntity> = buildList {
        val ids = mutableSetOf<Long>()
        repeat(array.length()) { index ->
            val context = "notes[$index]"
            val value = objectAt(array, index, "notes")
            requireKeys(value, setOf("id", "title", "body", "createdAt", "updatedAt"), context)
            val id = requiredPositiveLong(value, "id", context)
            if (!ids.add(id)) throw BackupValidationException("$context.id is duplicated.")
            val title = requiredString(value, "title", LinkNestBackupFormat.MAX_TEXT_LENGTH, context)
            if (title.isBlank()) throw BackupValidationException("$context.title cannot be blank.")
            add(
                NoteEntity(
                    id = id,
                    title = title,
                    body = requiredString(value, "body", LinkNestBackupFormat.MAX_TEXT_LENGTH, context),
                    createdAt = requiredNonNegativeLong(value, "createdAt", context),
                    updatedAt = requiredNonNegativeLong(value, "updatedAt", context)
                )
            )
        }
    }

    private fun parseTags(array: JSONArray): List<TagEntity> = buildList {
        val ids = mutableSetOf<Long>()
        val names = mutableSetOf<String>()
        repeat(array.length()) { index ->
            val context = "tags[$index]"
            val value = objectAt(array, index, "tags")
            requireKeys(value, setOf("id", "name"), context)
            val id = requiredPositiveLong(value, "id", context)
            if (!ids.add(id)) throw BackupValidationException("$context.id is duplicated.")
            val name = requiredString(value, "name", LinkNestBackupFormat.MAX_TAG_NAME_LENGTH, context)
            if (name.isBlank()) throw BackupValidationException("$context.name cannot be blank.")
            if (!names.add(name)) throw BackupValidationException("$context.name is duplicated.")
            add(TagEntity(id = id, name = name))
        }
    }

    private fun parseBookmarkTags(array: JSONArray): List<BookmarkTagCrossRef> = buildList {
        val pairs = mutableSetOf<Pair<Long, Long>>()
        repeat(array.length()) { index ->
            val context = "bookmarkTags[$index]"
            val value = objectAt(array, index, "bookmarkTags")
            requireKeys(value, setOf("bookmarkId", "tagId"), context)
            val ref = BookmarkTagCrossRef(
                bookmarkId = requiredPositiveLong(value, "bookmarkId", context),
                tagId = requiredPositiveLong(value, "tagId", context)
            )
            if (!pairs.add(ref.bookmarkId to ref.tagId)) throw BackupValidationException("$context is duplicated.")
            add(ref)
        }
    }

    private fun parseNoteTags(array: JSONArray): List<NoteTagCrossRef> = buildList {
        val pairs = mutableSetOf<Pair<Long, Long>>()
        repeat(array.length()) { index ->
            val context = "noteTags[$index]"
            val value = objectAt(array, index, "noteTags")
            requireKeys(value, setOf("noteId", "tagId"), context)
            val ref = NoteTagCrossRef(
                noteId = requiredPositiveLong(value, "noteId", context),
                tagId = requiredPositiveLong(value, "tagId", context)
            )
            if (!pairs.add(ref.noteId to ref.tagId)) throw BackupValidationException("$context is duplicated.")
            add(ref)
        }
    }

    private fun parseGraphEdges(array: JSONArray): List<GraphEdgeEntity> = buildList {
        val ids = mutableSetOf<Long>()
        val pairs = mutableSetOf<Pair<String, String>>()
        repeat(array.length()) { index ->
            val context = "graphEdges[$index]"
            val value = objectAt(array, index, "graphEdges")
            requireKeys(value, setOf("id", "sourceNodeKey", "targetNodeKey", "relationshipType", "createdAt"), context)
            val id = requiredPositiveLong(value, "id", context)
            if (!ids.add(id)) throw BackupValidationException("$context.id is duplicated.")
            val source = requiredString(value, "sourceNodeKey", LinkNestBackupFormat.MAX_NODE_KEY_LENGTH, context)
            val target = requiredString(value, "targetNodeKey", LinkNestBackupFormat.MAX_NODE_KEY_LENGTH, context)
            if (source >= target) throw BackupValidationException("$context endpoints are not in canonical order.")
            if (!pairs.add(source to target)) throw BackupValidationException("$context endpoints are duplicated.")
            add(
                GraphEdgeEntity(
                    id = id,
                    sourceNodeKey = source,
                    targetNodeKey = target,
                    relationshipType = requiredString(value, "relationshipType", 32, context),
                    createdAt = requiredNonNegativeLong(value, "createdAt", context)
                )
            )
        }
    }

    private fun requiredNullableUrl(value: JSONObject, key: String, context: String): String? {
        val raw = requiredNullableString(value, key, LinkNestBackupFormat.MAX_URL_LENGTH, context) ?: return null
        val normalized = UrlUtils.normalize(raw)
            ?: throw BackupValidationException("$context.$key is not a valid http or https URL.")
        if (normalized.canonical != raw) throw BackupValidationException("$context.$key is not normalized.")
        return raw
    }

    private fun validateRelationships(
        bookmarks: List<BookmarkEntity>,
        notes: List<NoteEntity>,
        tags: List<TagEntity>,
        bookmarkTags: List<BookmarkTagCrossRef>,
        noteTags: List<NoteTagCrossRef>,
        graphEdges: List<GraphEdgeEntity>
    ) {
        val bookmarkIds = bookmarks.map { it.id }.toSet()
        val noteIds = notes.map { it.id }.toSet()
        val tagIds = tags.map { it.id }.toSet()

        bookmarkTags.forEach { ref ->
            if (ref.bookmarkId !in bookmarkIds) {
                throw BackupValidationException("bookmarkTags references missing bookmark ${ref.bookmarkId}.")
            }
            if (ref.tagId !in tagIds) {
                throw BackupValidationException("bookmarkTags references missing tag ${ref.tagId}.")
            }
        }
        noteTags.forEach { ref ->
            if (ref.noteId !in noteIds) {
                throw BackupValidationException("noteTags references missing note ${ref.noteId}.")
            }
            if (ref.tagId !in tagIds) {
                throw BackupValidationException("noteTags references missing tag ${ref.tagId}.")
            }
        }
        graphEdges.forEach { edge ->
            if (edge.relationshipType != "EXPLICIT") {
                throw BackupValidationException("graphEdges contains an unsupported relationship type.")
            }
            validateNodeKey(edge.sourceNodeKey, bookmarkIds, noteIds, "graphEdges source")
            validateNodeKey(edge.targetNodeKey, bookmarkIds, noteIds, "graphEdges target")
            if (edge.sourceNodeKey == edge.targetNodeKey) {
                throw BackupValidationException("graphEdges cannot contain self-links.")
            }
        }
    }

    private fun validateNodeKey(key: String, bookmarkIds: Set<Long>, noteIds: Set<Long>, context: String) {
        val separator = key.indexOf(':')
        if (separator <= 0 || separator == key.lastIndex) {
            throw BackupValidationException("$context '$key' is invalid.")
        }
        val type = key.substring(0, separator)
        val id = key.substring(separator + 1).toLongOrNull()
            ?: throw BackupValidationException("$context '$key' is invalid.")
        if (id <= 0L) throw BackupValidationException("$context '$key' is invalid.")
        when (type) {
            "bookmark" -> if (id !in bookmarkIds) throw BackupValidationException("$context '$key' is dangling.")
            "note" -> if (id !in noteIds) throw BackupValidationException("$context '$key' is dangling.")
            else -> throw BackupValidationException("$context '$key' has an unsupported node type.")
        }
    }
}
