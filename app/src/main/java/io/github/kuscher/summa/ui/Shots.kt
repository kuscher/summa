package io.github.kuscher.summa.ui

import android.app.Presentation
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.kuscher.summa.SummaApp
import io.github.kuscher.summa.data.Library
import io.github.kuscher.summa.data.LibraryIndex
import io.github.kuscher.summa.data.SheetMeta
import io.github.kuscher.summa.ui.editor.Session
import io.github.kuscher.summa.ui.library.PhoneList
import io.github.kuscher.summa.ui.library.SheetActions
import io.github.kuscher.summa.ui.library.Sidebar
import io.github.kuscher.summa.ui.theme.SummaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.FileOutputStream

/**
 * Store screenshots (debug hooks only): renders Summa's real UI on a private virtual display at
 * any size and density (a phone at 1080×1920, a laptop at 1920×1080) and saves a PNG. The sheet
 * text is shown in a throwaway session that is never saved.
 */
object Shots {
    private val SAMPLE_LIST = listOf("Munich weekend", "Buying a flat", "Welcome to Summa", "Kitchen renovation", "Running pace", "Sourdough ratios")

    fun render(activity: ComponentActivity, kind: String, w: Int, h: Int, dpi: Int, dark: Boolean, text: String, out: File, done: (String) -> Unit) {
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        val dm = activity.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay("summa-shot", w, h, dpi, reader.surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
        val pres = Presentation(activity, vd.display, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
        val app = SummaApp.instance
        val settings = app.prefs.value.copy(dark = if (dark) "dark" else "light")
        val engineSettings = MutableStateFlow(app.prefs.engineSettings())
        val session = Session("shot-" + System.nanoTime(), text, app.library, engineSettings, app.rates.rates, activity.lifecycleScope)
        val now = System.currentTimeMillis()
        val index = LibraryIndex(sheets = SAMPLE_LIST.mapIndexed { i, t -> SheetMeta("s$i", created = now, updated = now - i * 86_400_000L * (if (i < 2) 0 else i), title = t) })
        val actions = SheetActions({}, null, {}, {})
        val view = ComposeView(pres.context).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides activity) {
                    SummaTheme(settings) {
                        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                            Shot(kind, session, index, actions)
                        }
                    }
                }
            }
        }
        pres.setContentView(view)
        pres.show()
        Handler(Looper.getMainLooper()).postDelayed({
            val result = try {
                val img = reader.acquireLatestImage() ?: throw IllegalStateException("no frame")
                val plane = img.planes[0]
                val rowPixels = plane.rowStride / plane.pixelStride
                val bmp = Bitmap.createBitmap(rowPixels, h, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(plane.buffer)
                img.close()
                val cropped = Bitmap.createBitmap(bmp, 0, 0, w, h)
                FileOutputStream(out).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                out.absolutePath
            } catch (e: Exception) { "failed ${e.message}" }
            pres.dismiss(); session.close(); vd.release(); reader.close()
            done(result)
        }, 3000)
    }

    @Composable
    private fun Shot(kind: String, session: Session, index: LibraryIndex, actions: SheetActions) {
        val app = SummaApp.instance
        val meta = SheetMeta(session.id, created = 0, updated = 0, title = Library.titleOf(session.state.text.toString()))
        val search = remember { androidx.compose.foundation.text.input.TextFieldState() }
        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
        DisposableEffect(Unit) { onDispose { } }
        when (kind) {
            "list" -> PhoneList(app.library, index, search, focus, actions, {}, {}, null, {})
            "phone" -> Column(Modifier.fillMaxSize()) {
                HeaderRow(MaterialTheme.colorScheme.surface) {
                    SheetHeader(session, meta, null, onBack = {}, onToggleSidebar = null, onSettings = {}, onMessage = {}, onNew = {}, onDelete = {}, onNewWindow = null, onMini = null)
                }
                EditorPane(session, false, Modifier.weight(1f), onMessage = {})
            }
            else -> {
                // "large" (with the sheet list) or "focus" (list hidden): a laptop window.
                val withList = kind == "large"
                val bar = if (withList) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface
                Column(Modifier.fillMaxSize()) {
                    HeaderRow(bar) {
                        SheetHeader(session, meta, null, onBack = null, onToggleSidebar = {}, onSettings = {}, onMessage = {}, onNew = {}, onDelete = {},
                            onNewWindow = {}, onMini = {})
                    }
                    Row(Modifier.weight(1f)) {
                        if (withList) Sidebar(app.library, index.copy(sheets = index.sheets.mapIndexed { i, m -> if (i == 0) m.copy(id = session.id, title = meta.title) else m }),
                            session.id, search, focus, actions, {}, null, {}, Modifier.width(256.dp))
                        EditorPane(session, true, Modifier.weight(1f), onMessage = {})
                    }
                }
            }
        }
    }
}
