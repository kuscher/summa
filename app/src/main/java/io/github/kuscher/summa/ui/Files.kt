package io.github.kuscher.summa.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import io.github.kuscher.summa.data.Library
import io.github.kuscher.summa.ui.editor.Session

/** Export through the system file picker (no storage permission needed). */
class FileActions(
    val exportText: () -> Unit, val exportMarkdown: () -> Unit,
    val exportCsv: () -> Unit, val exportHtml: () -> Unit, val exportPdf: () -> Unit,
)

@Composable
fun rememberFileActions(session: Session?, onMessage: (String) -> Unit): FileActions {
    val context = androidx.compose.ui.platform.LocalContext.current
    val saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null && session != null) { write(context, uri, session.state.text.toString()); onMessage("Exported") }
    }
    val saveMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null && session != null) { write(context, uri, markdown(session)); onMessage("Exported") }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null && session != null) { write(context, uri, Export.csv(SheetSnapshot.of(session))); onMessage("Exported") }
    }
    val saveHtml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri != null && session != null) { write(context, uri, Export.html(context, SheetSnapshot.of(session))); onMessage("Exported") }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && session != null) {
            val snap = SheetSnapshot.of(session)
            val (w, h) = Export.defaultPage(context)
            val pages = try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { Export.pdf(context, snap, it, w, h) }
            } catch (_: Exception) { null }
            onMessage(if (pages == null) "Couldn't write the PDF" else "Exported")
        }
    }
    fun fileName(ext: String) = (Library.titleOf(session?.state?.text?.toString().orEmpty()).ifBlank { "Summa sheet" }
        .replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()) + ext
    return FileActions(
        exportText = { saveText.launch(fileName(".txt")) },
        exportMarkdown = { saveMd.launch(fileName(".md")) },
        exportCsv = { saveCsv.launch(fileName(".csv")) },
        exportHtml = { saveHtml.launch(fileName(".html")) },
        exportPdf = { savePdf.launch(fileName(".pdf")) },
    )
}

private fun write(context: Context, uri: Uri, text: String) {
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
}

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
