package io.github.kuscher.summa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.PixelCopy
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.text.TextRange
import io.github.kuscher.summa.ui.DebugHooks
import io.github.kuscher.summa.ui.MainActivity
import java.io.File
import java.io.FileOutputStream

/**
 * Test hooks for driving Summa from adb (`./summa debug …`). The receiver requires the DUMP
 * permission, which only the shell (adb) holds, so other apps can't use it.
 *   list | open ID | new | text BASE64 | cursor N | dump | shot [NAME] | screen sheet|settings | pref KEY VALUE
 *   export pdf|html|csv (writes cache/export.EXT) | defs (the definitions sheet's names)
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("c") ?: return
        val parts = cmd.split(' ', limit = 2)
        val arg = parts.getOrNull(1).orEmpty()
        val app = SummaApp.instance
        val act = MainActivity.current.get()
        val main = Handler(Looper.getMainLooper())
        fun out(s: String) { Log.i(TAG, "debug ${parts[0]} -> $s") }
        when (parts[0]) {
            "list" -> out(app.library.state.value.sheets.joinToString(" | ") { "${it.id}:${it.title}${if (it.trashedAt != null) " (trash)" else ""}" })
            "open" -> main.post { if (arg == "definitions") app.library.definitions(); DebugHooks.open(arg); out("ok") }
            "new" -> main.post { DebugHooks.newSheet(); out("ok") }
            "delete" -> { app.library.deleteForever(arg); out("ok") }
            "screen" -> main.post { DebugHooks.screen(arg); out("ok") }
            "focus" -> main.post { DebugHooks.focus(); out("ok") }
            "mini" -> main.post { io.github.kuscher.summa.ui.MainActivity.openMini(act ?: context.applicationContext); out("ok") }
            "minitext" -> main.post {
                val s = io.github.kuscher.summa.ui.MiniActivity.instance.get()?.session ?: return@post out("no mini")
                s.state.setTextAndPlaceCursorAtEnd(String(Base64.decode(arg, Base64.DEFAULT), Charsets.UTF_8)); out("ok")
            }
            "tomini" -> main.post { act?.let { io.github.kuscher.summa.ui.MainActivity.switchToMini(it) }; out(if (act != null) "ok" else "no activity") }
            "tobig" -> main.post { io.github.kuscher.summa.ui.MiniActivity.instance.get()?.backToBig(); out("ok") }
            "closemini" -> main.post { io.github.kuscher.summa.ui.MiniActivity.instance.get()?.finishAndRemoveTask(); out("ok") }
            "text" -> main.post {
                val s = act?.session ?: return@post out("no session")
                s.state.setTextAndPlaceCursorAtEnd(String(Base64.decode(arg, Base64.DEFAULT), Charsets.UTF_8))
                out("ok")
            }
            "type" -> main.post {
                // Types into Summa's own text state (like a keystroke, without injecting input).
                val s = act?.session ?: return@post out("no session")
                s.state.edit { val at = selection.max; replace(selection.min, at, arg); selection = TextRange(selection.min + arg.length) }
                out("ok")
            }
            "cursor" -> main.post {
                val s = act?.session ?: return@post out("no session")
                val (a, b) = arg.split(' ').map { it.toInt() }.let { it[0] to (it.getOrNull(1) ?: it[0]) }
                s.state.edit { selection = TextRange(a.coerceIn(0, length), b.coerceIn(0, length)) }
                out("ok")
            }
            "dump" -> main.post {
                val ev = act?.session?.evaluated ?: return@post out("no result")
                ev.result.lines.forEachIndexed { i, l -> Log.i(TAG, "line ${i + 1}: ${l.answer ?: "∅"}${l.error?.let { "  ($it)" } ?: ""}") }
                out("${ev.result.lines.size} lines, evaluated in ${"%.1f".format(act.session?.lastEvalMs ?: 0.0)} ms")
            }
            "pref" -> {
                val (k, v) = arg.split(' ', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                app.prefs.update { s ->
                    when (k) {
                        "theme" -> s.copy(theme = v); "dark" -> s.copy(dark = v); "lineNumbers" -> s.copy(lineNumbers = v == "true")
                        "textSize" -> s.copy(textSize = v.toFloat()); "sidebar" -> s.copy(sidebar = v == "true")
                        "decimals" -> s.copy(decimals = v.toInt())
                        else -> s
                    }
                }
                out("ok")
            }
            "shot" -> main.post {
                // Our own window only (PixelCopy of this activity's surface): no other apps, no pointer.
                // Whichever Summa window is open: the big one, or the mini one.
                // shot NAME: names starting "mini" or "calc" capture those windows.
                val mini = io.github.kuscher.summa.ui.MiniActivity.instance.get()?.takeIf { !it.isDestroyed && !it.isFinishing }
                val calc = io.github.kuscher.summa.ui.CalculateActivity.instance.get()?.takeIf { !it.isDestroyed && !it.isFinishing }
                val a: android.app.Activity = (if (arg.startsWith("mini")) mini else if (arg.startsWith("calc")) calc else null)
                    ?: act?.takeIf { !it.isDestroyed && !it.isFinishing } ?: mini
                    ?: return@post out("no activity")
                val v = a.window.decorView
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                try { PixelCopy.request(a.window, bmp, { res ->
                    if (res != PixelCopy.SUCCESS) { out("failed $res"); return@request }
                    val dir = a.cacheDir
                    val f = File(dir, (arg.ifBlank { "shot" }) + ".png")
                    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    out(f.absolutePath)
                }, main) } catch (e: Exception) { out("failed ${e.message}") }
            }
            "export" -> main.post {
                val s = act?.session ?: return@post out("no session")
                val snap = io.github.kuscher.summa.ui.SheetSnapshot.of(s)
                val f = File(context.cacheDir, "export.$arg")
                when (arg) {
                    "pdf" -> FileOutputStream(f).use { io.github.kuscher.summa.ui.Export.pdf(context, snap, it, 595, 842) }
                    "html" -> f.writeText(io.github.kuscher.summa.ui.Export.html(context, snap))
                    "csv" -> f.writeText(io.github.kuscher.summa.ui.Export.csv(snap))
                }
                out(f.absolutePath)
            }
            "print" -> main.post {
                val a = act ?: return@post out("no activity")
                val s = a.session ?: return@post out("no session")
                io.github.kuscher.summa.ui.Export.print(a, io.github.kuscher.summa.ui.SheetSnapshot.of(s)); out("ok")
            }
            "shareuri" -> {
                // The FileProvider path Share › Share as PDF uses, without opening the share sheet.
                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                val f = File(dir, "probe.pdf").apply { writeText("probe") }
                out(androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", f).toString())
            }
            "render" -> main.post {
                // render KIND W H DPI DARK(0|1) BASE64TEXT → cache/render.png (store screenshots; see Shots.kt)
                val a = act ?: return@post out("no activity")
                val p = arg.split(' ')
                val text = String(Base64.decode(p.getOrElse(5) { "" }, Base64.DEFAULT), Charsets.UTF_8)
                io.github.kuscher.summa.ui.Shots.render(a, p[0], p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4] == "1", text,
                    File(context.cacheDir, "render.png")) { out(it) }
            }
            "defs" -> { val d = app.definitions.value; out("vars=${d.vars.keys} units=${d.units.map { it.names }} fns=${d.functions.keys}") }
            "holidays" -> { app.prefs.update { it.copy(holidays = arg) }; out(app.prefs.holidayCountry()) }
            else -> out("unknown")
        }
    }

    companion object { const val TAG = "Summa" }
}
