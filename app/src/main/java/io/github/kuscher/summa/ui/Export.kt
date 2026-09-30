package io.github.kuscher.summa.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import io.github.kuscher.summa.R
import io.github.kuscher.summa.SummaApp
import io.github.kuscher.summa.data.Library
import io.github.kuscher.summa.engine.LineKind
import io.github.kuscher.summa.engine.LineResult
import io.github.kuscher.summa.engine.Style
import io.github.kuscher.summa.ui.editor.Session
import io.github.kuscher.summa.ui.theme.schemeFor
import io.github.kuscher.summa.ui.theme.summaColors
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A sheet as exported: its lines and their answers at one moment. */
class SheetSnapshot(val title: String, val lines: List<String>, val results: List<LineResult?>) {
    companion object {
        fun of(s: Session): SheetSnapshot {
            val text = s.state.text.toString()
            val ev = s.evaluated?.takeIf { it.text == text }
            val lines = text.split('\n')
            return SheetSnapshot(Library.titleOf(text).ifBlank { "Summa sheet" }, lines, lines.indices.map { ev?.result?.lines?.getOrNull(it) })
        }
    }
}

/** CSV, HTML and PDF versions of a sheet, and printing (which draws the same pages as the PDF). */
object Export {
    // ------------------------------------------------------------------ CSV

    /** Two columns, Line and Answer. UTF-8 with a byte-order mark so spreadsheets read € and £ right. */
    fun csv(s: SheetSnapshot): String {
        fun q(v: String) = if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
        val sb = StringBuilder("\uFEFFLine,Answer\r\n")
        for ((i, l) in s.lines.withIndex()) sb.append(q(l)).append(',').append(q(s.results[i]?.answer.orEmpty())).append("\r\n")
        return sb.toString()
    }

    // ------------------------------------------------------------------ colours shared by HTML and PDF

    private class Palette(val text: Int, val muted: Int, val primary: Int, val pill: Int, val onPill: Int, val rule: Int, val styles: Map<Style, Int>)

    private fun palette(context: Context): Palette {
        val scheme = schemeFor(SummaApp.instance.prefs.value.theme, false, context)
        val c = summaColors(scheme)
        return Palette(
            text = scheme.onSurface.toArgb(), muted = scheme.onSurfaceVariant.toArgb(), primary = scheme.primary.toArgb(),
            pill = scheme.primaryContainer.toArgb(), onPill = scheme.onPrimaryContainer.toArgb(), rule = scheme.outlineVariant.toArgb(),
            styles = mapOf(
                Style.NUMBER to c.number.toArgb(), Style.UNIT to c.unit.toArgb(), Style.VARIABLE to c.variable.toArgb(),
                Style.KEYWORD to c.keyword.toArgb(), Style.FUNCTION to c.function.toArgb(), Style.LABEL to c.label.toArgb(),
                Style.COMMENT to c.comment.toArgb(), Style.HEADING to c.heading.toArgb(), Style.REFERENCE to c.reference.toArgb(),
                Style.AGGREGATE to c.reference.toArgb(), Style.DATE to c.date.toArgb(), Style.TAG to c.function.toArgb(),
                Style.OPERATOR to c.keyword.toArgb(),
            ),
        )
    }

    private fun hex(argb: Int) = String.format("#%06X", argb and 0xFFFFFF)
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun headingText(l: String) = l.trimStart().trimStart('#').trim()
    private fun today() = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    // ------------------------------------------------------------------ HTML

    /** A self-contained page (no scripts, no web fonts) that looks like the sheet. */
    fun html(context: Context, s: SheetSnapshot): String {
        val p = palette(context)
        val sb = StringBuilder()
        sb.append("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        sb.append("<meta name=\"generator\" content=\"Summa\"><title>").append(esc(s.title)).append("</title>\n<style>\n")
        sb.append("""
            body{margin:0;background:#fff;color:${hex(p.text)};font:16px/1.55 "Google Sans Flex","Google Sans",system-ui,-apple-system,"Segoe UI",sans-serif}
            main{max-width:820px;margin:48px auto;padding:0 24px}
            .r{display:grid;grid-template-columns:minmax(0,1fr) minmax(120px,240px);column-gap:24px;align-items:baseline;padding:1px 0}
            .l{white-space:pre-wrap;overflow-wrap:anywhere}
            .a{text-align:right;font-weight:650;color:${hex(p.primary)};font-variant-numeric:tabular-nums;white-space:nowrap}
            .t .a span{background:${hex(p.pill)};color:${hex(p.onPill)};border-radius:999px;padding:2px 12px}
            .h{font-size:1.45em;font-weight:760;margin:1em 0 .2em;letter-spacing:-.01em}
            .r:first-child .h{margin-top:0}
            .c{color:${hex(p.styles[Style.COMMENT]!!)}}
            .e{height:.8em}
            hr{border:0;border-top:1px solid ${hex(p.rule)};margin:.6em 0}
            footer{margin-top:40px;padding-top:12px;border-top:1px solid ${hex(p.rule)};color:${hex(p.muted)};font-size:.8em}
            @media print{main{margin:0;max-width:none}}
        """.trimIndent())
        sb.append("\n</style></head><body><main>\n")
        for ((i, l) in s.lines.withIndex()) {
            val r = s.results[i]
            val kind = r?.kind ?: if (l.isBlank()) LineKind.EMPTY else LineKind.EXPR
            when (kind) {
                LineKind.EMPTY -> sb.append("<div class=\"e\"></div>\n")
                LineKind.DIVIDER -> sb.append("<hr>\n")
                LineKind.HEADING -> sb.append("<div class=\"r\"><div class=\"l h\">").append(esc(headingText(l))).append("</div><div></div></div>\n")
                LineKind.COMMENT -> sb.append("<div class=\"r\"><div class=\"l c\">").append(esc(l.trimStart().removePrefix("//").trim())).append("</div><div></div></div>\n")
                LineKind.EXPR -> {
                    sb.append("<div class=\"r").append(if (r?.isTotal == true) " t" else "").append("\"><div class=\"l\">")
                    var at = 0
                    for (sp in r?.spans.orEmpty().sortedBy { it.start }) {
                        val a = sp.start.coerceIn(at, l.length); val b = sp.end.coerceIn(a, l.length)
                        if (a > at) sb.append(esc(l.substring(at, a)))
                        if (b > a) sb.append("<span style=\"color:").append(hex(p.styles[sp.style] ?: p.text)).append("\">").append(esc(l.substring(a, b))).append("</span>")
                        at = b
                    }
                    if (at < l.length) sb.append(esc(l.substring(at)))
                    sb.append("</div><div class=\"a\">")
                    r?.answer?.let { sb.append("<span>").append(esc(it)).append("</span>") }
                    sb.append("</div></div>\n")
                }
            }
        }
        sb.append("<footer>").append(esc(s.title)).append(" · ").append(esc(today())).append(" · made with Summa</footer>\n</main></body></html>\n")
        return sb.toString()
    }

    // ------------------------------------------------------------------ PDF

    /** US-style paper for the Americas, A4 elsewhere; in PostScript points. */
    fun defaultPage(context: Context): Pair<Int, Int> {
        val country = context.resources.configuration.locales[0]?.country
        return if (country in setOf("US", "CA", "MX", "PH", "CL", "CO", "VE")) 612 to 792 else 595 to 842
    }

    /** Draws the sheet onto pages of [pageW] × [pageH] points. Returns the page count. */
    fun pdf(context: Context, s: SheetSnapshot, out: OutputStream, pageW: Int, pageH: Int): Int {
        val doc = PdfDocument()
        try {
            val count = draw(context, s, doc, pageW, pageH)
            doc.writeTo(out)
            return count
        } finally { doc.close() }
    }

    private fun draw(context: Context, s: SheetSnapshot, doc: PdfDocument, pageW: Int, pageH: Int): Int {
        val p = palette(context)
        val sans = ResourcesCompat.getFont(context, R.font.summa_sans)
        fun paint(size: Float, weight: Int, color: Int, round: Int = 0) = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = sans; textSize = size; this.color = color
            fontVariationSettings = "'wght' $weight, 'ROND' $round, 'opsz' ${size.coerceIn(8f, 144f)}"
        }
        val body = paint(10.5f, 400, p.text)
        val heading = paint(15.5f, 760, p.text)
        val comment = paint(10.5f, 400, p.styles[Style.COMMENT]!!)
        val answer = paint(10.5f, 650, p.primary, round = 100).apply { fontFeatureSettings = "tnum" }
        val totalAnswer = paint(10.5f, 700, p.onPill, round = 100).apply { fontFeatureSettings = "tnum" }
        val footer = paint(8f, 450, p.muted)
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.rule; strokeWidth = 0.6f }
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.pill }

        val margin = 50f
        val answerW = minOf(190f, (pageW - 2 * margin) * 0.34f)
        val gap = 18f
        val textW = (pageW - 2 * margin - answerW - gap).toInt()
        val bottom = pageH - margin - 18f

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun finishPage() {
            val pg = page ?: return
            val c = pg.canvas
            c.drawText(s.title, margin, pageH - margin + 6f, footer)
            val n = "$pageNo"
            c.drawText(n, pageW - margin - footer.measureText(n), pageH - margin + 6f, footer)
            doc.finishPage(pg)
            page = null
        }
        fun newPage() {
            finishPage()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            y = margin
        }
        fun layout(text: CharSequence, tp: TextPaint, width: Int, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) =
            StaticLayout.Builder.obtain(text, 0, text.length, tp, width).setAlignment(align).setLineSpacing(1.5f, 1f).setIncludePad(false).build()

        newPage()
        for ((i, l) in s.lines.withIndex()) {
            val r = s.results[i]
            val kind = r?.kind ?: if (l.isBlank()) LineKind.EMPTY else LineKind.EXPR
            when (kind) {
                LineKind.EMPTY -> { y += 7f; continue }
                LineKind.DIVIDER -> {
                    if (y + 12f > bottom) newPage()
                    page!!.canvas.drawLine(margin, y + 6f, pageW - margin, y + 6f, rule); y += 12f; continue
                }
                else -> {}
            }
            val (text, tp) = when (kind) {
                LineKind.HEADING -> headingText(l) to heading
                LineKind.COMMENT -> l.trimStart().removePrefix("//").trim() to comment
                else -> {
                    val sp = SpannableString(l)
                    for (x in r?.spans.orEmpty()) {
                        val a = x.start.coerceIn(0, l.length); val b = x.end.coerceIn(a, l.length)
                        if (b > a) sp.setSpan(ForegroundColorSpan(p.styles[x.style] ?: p.text), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    sp to body
                }
            }
            val topPad = if (kind == LineKind.HEADING && y > margin) 10f else 0f
            val left = layout(text, tp, textW)
            val ans = r?.answer
            val isTotal = r?.isTotal == true
            val right = ans?.let { layout(it, if (isTotal) totalAnswer else answer, answerW.toInt(), Layout.Alignment.ALIGN_OPPOSITE) }
            val rowH = topPad + maxOf(left.height, right?.height ?: 0) + 3f
            if (y + rowH > bottom) newPage()
            val c = page!!.canvas
            y += if (y == margin) 0f else topPad
            c.save(); c.translate(margin, y); left.draw(c); c.restore()
            if (right != null) {
                val x = margin + textW + gap
                if (isTotal) {
                    val w = right.getLineWidth(0) + 16f
                    c.drawRoundRect(RectF(x + answerW - w, y - 2f, x + answerW + 2f, y + right.height + 1f), 99f, 99f, pillPaint)
                    c.save(); c.translate(x - 6f, y); right.draw(c); c.restore()
                } else {
                    c.save(); c.translate(x, y); right.draw(c); c.restore()
                }
            }
            y += maxOf(left.height, right?.height ?: 0) + 3f
        }
        finishPage()
        return pageNo
    }

    // ------------------------------------------------------------------ sharing

    /** Writes the sheet as a PDF in the cache and opens the system share sheet with it. */
    fun sharePdf(context: Context, s: SheetSnapshot) {
        val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = s.title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "Summa sheet" } + ".pdf"
        val f = java.io.File(dir, name)
        val (w, h) = defaultPage(context)
        f.outputStream().use { pdf(context, s, it, w, h) }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", f)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(android.content.Intent.EXTRA_STREAM, uri)
            .putExtra(android.content.Intent.EXTRA_SUBJECT, s.title)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = android.content.ClipData.newRawUri(name, uri)
        context.startActivity(android.content.Intent.createChooser(send, "Share ${s.title}"))
    }

    // ------------------------------------------------------------------ printing

    /** Opens the system print dialog (which can also save a PDF) with the sheet's pages. */
    fun print(context: Context, s: SheetSnapshot) {
        val pm = context.getSystemService(PrintManager::class.java) ?: return
        pm.print(s.title, SheetPrintAdapter(context, s), PrintAttributes.Builder().setMediaSize(
            if (defaultPage(context).first == 612) PrintAttributes.MediaSize.NA_LETTER else PrintAttributes.MediaSize.ISO_A4).build())
    }

    private class SheetPrintAdapter(private val context: Context, private val s: SheetSnapshot) : PrintDocumentAdapter() {
        private var size = 595 to 842

        override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
            if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
            val media = new.mediaSize ?: PrintAttributes.MediaSize.ISO_A4
            val w = media.widthMils * 72 / 1000; val h = media.heightMils * 72 / 1000
            size = if (media.isPortrait) w to h else maxOf(w, h) to minOf(w, h)
            val info = PrintDocumentInfo.Builder(s.title.replace(Regex("[\\\\/:*?\"<>|]"), " ") + ".pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build()
            cb.onLayoutFinished(info, old != new)
        }

        override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(dest).use { pdf(context, s, it, size.first, size.second) }
                cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                cb.onWriteFailed(e.message)
            }
        }
    }
}
