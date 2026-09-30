package io.github.kuscher.summa.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class SheetMeta(
    val id: String,
    val folder: String? = null,
    val pinned: Boolean = false,
    val trashedAt: Long? = null,
    val created: Long,
    val updated: Long,
    val title: String = "",
    /** The sheet's total as last shown, for the sidebar. */
    val total: String? = null,
    /** Words from the sheet for search, refreshed on save. */
    val preview: String = "",
)

@Serializable
data class FolderMeta(val id: String, val name: String, val created: Long)

@Serializable
data class LibraryIndex(val sheets: List<SheetMeta> = emptyList(), val folders: List<FolderMeta> = emptyList(), val version: Int = 1)

/**
 * The sheet library. Each sheet is a plain text file in files/sheets/<id>.txt, with a small JSON
 * index beside it. Android's Auto Backup copies the folder to the user's Google account.
 * Writes are atomic (write a temp file, then rename) so a crash never leaves half a sheet.
 */
class Library(context: Context) {
    private val dir = File(context.filesDir, "sheets").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LibraryIndex> = _state.asStateFlow()

    private fun load(): LibraryIndex {
        val idx = try {
            if (indexFile.exists()) json.decodeFromString(LibraryIndex.serializer(), indexFile.readText()) else LibraryIndex()
        } catch (_: Exception) { LibraryIndex() }
        // Sheets whose files exist but aren't indexed (e.g. restored from backup) are re-added.
        val known = idx.sheets.map { it.id }.toSet()
        val orphans = dir.listFiles { f -> f.name.endsWith(".txt") }.orEmpty().map { it.nameWithoutExtension }.filter { it !in known }
        if (orphans.isEmpty()) return idx
        val now = System.currentTimeMillis()
        return idx.copy(sheets = idx.sheets + orphans.map { id ->
            val text = file(id).readText()
            SheetMeta(id, created = now, updated = file(id).lastModified(), title = titleOf(text), preview = previewOf(text))
        })
    }

    private fun file(id: String) = File(dir, "$id.txt")

    private fun atomicWrite(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.writeText(text); tmp.delete() }
    }

    @Synchronized private fun commit(idx: LibraryIndex) {
        _state.value = idx
        atomicWrite(indexFile, json.encodeToString(LibraryIndex.serializer(), idx))
    }

    private fun edit(f: (LibraryIndex) -> LibraryIndex) = synchronized(this) { commit(f(_state.value)) }
    private fun editSheet(id: String, f: (SheetMeta) -> SheetMeta) = edit { idx -> idx.copy(sheets = idx.sheets.map { if (it.id == id) f(it) else it }) }

    fun get(id: String): SheetMeta? = _state.value.sheets.firstOrNull { it.id == id }
    fun text(id: String): String = try { file(id).readText() } catch (_: Exception) { "" }

    fun create(text: String = "", folder: String? = null): SheetMeta {
        val now = System.currentTimeMillis()
        val meta = SheetMeta(UUID.randomUUID().toString().take(12), folder, created = now, updated = now,
            title = titleOf(text), preview = previewOf(text))
        atomicWrite(file(meta.id), text)
        edit { it.copy(sheets = it.sheets + meta) }
        return meta
    }

    fun save(id: String, text: String, total: String?) {
        if (get(id) == null) return
        atomicWrite(file(id), text)
        editSheet(id) { it.copy(updated = System.currentTimeMillis(), title = titleOf(text), total = total, preview = previewOf(text)) }
    }

    fun setTotal(id: String, total: String?) { if (get(id)?.total != total) editSheet(id) { it.copy(total = total) } }
    fun pin(id: String, pinned: Boolean) = editSheet(id) { it.copy(pinned = pinned) }
    fun trash(id: String) = editSheet(id) { it.copy(trashedAt = System.currentTimeMillis(), pinned = false) }
    fun restore(id: String) = editSheet(id) { it.copy(trashedAt = null) }
    fun move(id: String, folder: String?) = editSheet(id) { it.copy(folder = folder) }

    fun deleteForever(id: String) {
        file(id).delete()
        edit { idx -> idx.copy(sheets = idx.sheets.filter { it.id != id }) }
    }

    /** Deleted sheets stay a while (Undo, backups) and go for good after [ageMs]. */
    fun purgeTrash(ageMs: Long) {
        val cutoff = System.currentTimeMillis() - ageMs
        val gone = _state.value.sheets.filter { (it.trashedAt ?: Long.MAX_VALUE) < cutoff }
        if (gone.isEmpty()) return
        gone.forEach { file(it.id).delete() }
        val ids = gone.map { it.id }.toSet()
        edit { idx -> idx.copy(sheets = idx.sheets.filter { it.id !in ids }) }
    }

    fun emptyTrash() {
        val gone = _state.value.sheets.filter { it.trashedAt != null }
        gone.forEach { file(it.id).delete() }
        edit { idx -> idx.copy(sheets = idx.sheets.filter { it.trashedAt == null }) }
    }

    fun duplicate(id: String): SheetMeta {
        val src = get(id)
        val text = text(id)
        val first = text.lineSequence().firstOrNull().orEmpty()
        val copyText = if (first.startsWith("#")) first + " (copy)" + text.removePrefix(first) else text
        return create(copyText, src?.folder)
    }

    fun createFolder(name: String): FolderMeta {
        val f = FolderMeta(UUID.randomUUID().toString().take(8), name, System.currentTimeMillis())
        edit { it.copy(folders = it.folders + f) }
        return f
    }

    fun renameFolder(id: String, name: String) = edit { idx -> idx.copy(folders = idx.folders.map { if (it.id == id) it.copy(name = name) else it }) }

    fun deleteFolder(id: String) = edit { idx ->
        idx.copy(folders = idx.folders.filter { it.id != id }, sheets = idx.sheets.map { if (it.folder == id) it.copy(folder = null) else it })
    }

    /** Sheets whose title or text contains every word of [query]. */
    fun search(query: String): List<SheetMeta> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return _state.value.sheets.filter { it.trashedAt == null && !isSpecial(it.id) }.filter { m ->
            val hay = (m.title + "\n" + text(m.id)).lowercase()
            words.all { it in hay }
        }.sortedByDescending { it.updated }
    }

    /** The mini calculator's scratch sheet: a normal file, hidden from the sheet list. */
    fun scratch(): SheetMeta {
        get(SCRATCH)?.let { return it }
        val now = System.currentTimeMillis()
        val text = "// Mini calculator: quick sums that stay on top.\n"
        val meta = SheetMeta(SCRATCH, created = now, updated = now, title = "Scratch")
        atomicWrite(file(SCRATCH), text)
        edit { it.copy(sheets = it.sheets + meta) }
        return meta
    }

    /**
     * The definitions sheet: its variables, units and functions work in every sheet. A normal
     * file (so it's backed up and exported like any sheet), shown apart from the sheet list.
     */
    fun definitions(): SheetMeta {
        get(DEFINITIONS)?.let { return it }
        val now = System.currentTimeMillis()
        val meta = SheetMeta(DEFINITIONS, created = now, updated = now, title = "Definitions")
        atomicWrite(file(DEFINITIONS), DEFINITIONS_START)
        edit { it.copy(sheets = it.sheets + meta) }
        return meta
    }

    companion object {
        const val SCRATCH = "scratch"
        const val DEFINITIONS = "definitions"

        /** Sheets that live outside the sheet list. */
        fun isSpecial(id: String) = id == SCRATCH || id == DEFINITIONS

        val DEFINITIONS_START = """
            # Definitions
            // Everything here works in every sheet. Some ideas:

            // Variables
            vat = 20%
            hourly = $85/hour

            // Your own units
            1 coffee = $4.50
            1 sprint = 2 weeks

            // Functions
            tip(bill) = bill × 18%
            bmi(weight, height) = weight / height²
        """.trimIndent() + "\n"

        fun titleOf(text: String): String {
            val first = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return ""
            return first.trimStart('#', ' ').removePrefix("//").trim().take(80)
        }

        fun previewOf(text: String): String = text.lineSequence().drop(1).map { it.trim() }.filter { it.isNotEmpty() }.take(3).joinToString(" · ").take(120)
    }
}
