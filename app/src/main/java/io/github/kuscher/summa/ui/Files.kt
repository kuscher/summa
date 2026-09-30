package io.github.kuscher.summa.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import io.github.kuscher.summa.SummaApp
import io.github.kuscher.summa.data.Library
import io.github.kuscher.summa.ui.editor.Session

/** Import and export through the system file picker (no storage permission needed). */
class FileActions(val exportText: () -> Unit, val exportMarkdown: () -> Unit, val import: () -> Unit)

@Composable
fun rememberFileActions(session: Session?, onMessage: (String) -> Unit, onOpen: (String) -> Unit): FileActions {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = SummaApp.instance
    val saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null && session != null) { write(context, uri, session.state.text.toString()); onMessage("Exported") }
    }
    val saveMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null && session != null) { write(context, uri, markdown(session)); onMessage("Exported with answers") }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        var last: String? = null
        for (u in uris) {
            val text = try { context.contentResolver.openInputStream(u)?.bufferedReader()?.readText() } catch (_: Exception) { null } ?: continue
            val name = displayName(context, u)?.substringBeforeLast('.')
            val body = if (name != null && Library.titleOf(text).isBlank()) "# $name\n$text" else text
            last = app.library.create(body.replace("\r\n", "\n")).id
        }
        if (uris.isNotEmpty()) onMessage(if (uris.size == 1) "Imported 1 sheet" else "Imported ${uris.size} sheets")
        last?.let(onOpen)
    }
    fun fileName(ext: String) = (Library.titleOf(session?.state?.text?.toString().orEmpty()).ifBlank { "Summa sheet" }
        .replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()) + ext
    return FileActions(
        exportText = { saveText.launch(fileName(".txt")) },
        exportMarkdown = { saveMd.launch(fileName(".md")) },
        import = { open.launch(arrayOf("text/*", "application/octet-stream")) },
    )
}

private fun write(context: Context, uri: Uri, text: String) {
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
}

private fun displayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (_: Exception) { null }

/** Markdown: headings stay headings, other lines become "line — **answer**". */
fun markdown(s: Session): String {
    val ev = s.evaluated
    return s.state.text.toString().split('\n').mapIndexed { i, l ->
        val a = ev?.result?.lines?.getOrNull(i)?.answer
        when {
            l.trimStart().startsWith("#") -> l
            l.trimStart().startsWith("//") -> "> " + l.trimStart().removePrefix("//").trim()
            a == null -> l
            else -> "$l — **$a**  "
        }
    }.joinToString("\n")
}
