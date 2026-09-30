package io.github.kuscher.summa.ui

import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.OutcomeReceiver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.summa.SummaApp
import io.github.kuscher.summa.data.Library
import io.github.kuscher.summa.ui.editor.Session
import io.github.kuscher.summa.ui.theme.SummaTheme
import kotlinx.coroutines.launch

/**
 * The mini calculator: the sheet you were on, in a small window (the same live sheet as the big
 * window, see Sessions). On Android 17 desktops it can stay above other windows (the pinned
 * windowing layer); the system parks a pinned window bottom-right.
 */
class MiniActivity : ComponentActivity() {
    private var pinned by mutableStateOf(false)
    /** The sheet shown here, and its session (the latter for the debug hooks). */
    private var sheetId by mutableStateOf("")
    var session: Session? = null
    private var autoPinTried = false
    private var onMessage: (String) -> Unit = {}
    private lateinit var captionTracker: CaptionTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        instance = java.lang.ref.WeakReference(this)
        captionTracker = CaptionTracker(this)
        val app = SummaApp.instance
        sheetId = pickSheet(intent)
        setContent {
            val settings by app.prefs.state.collectAsState()
            val scope = rememberCoroutineScope()
            SummaTheme(settings) {
                var status by remember { mutableStateOf<String?>(null) }
                onMessage = { m -> status = m; scope.launch { kotlinx.coroutines.delay(2600); if (status == m) status = null } }
                val id = sheetId
                val session = remember(id) { app.sessions.acquire(id) }
                DisposableEffect(session) {
                    this@MiniActivity.session = session
                    onDispose { app.sessions.release(session) }
                }
                val index by app.library.state.collectAsState()
                val title = index.sheets.firstOrNull { it.id == id }?.title?.ifBlank { null } ?: "Untitled"
                val bar = MaterialTheme.colorScheme.surface
                SideEffect { Caption.enable(this, bar.luminance() > 0.5f) }
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                        .onPreviewKeyEvent { e ->
                            // Ctrl+Shift+M goes back, the same keys that opened the mini window.
                            if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.isCtrlPressed && e.isShiftPressed &&
                                e.key == androidx.compose.ui.input.key.Key.M) { backToBig(); true } else false
                        }) {
                        Column(Modifier.fillMaxSize()) {
                            val cap = rememberCaptionInsets(captionTracker)
                            CaptionSpacer(cap, MaterialTheme.colorScheme.surface)
                            HeaderRow(MaterialTheme.colorScheme.surface) {
                                Text(title, Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight(650)),
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                status?.let { Text(it, Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                                HeaderButton(Sym.OPEN_IN_FULL, "Back to the big window", "Ctrl+Shift+M") { backToBig() }
                                if (Build.VERSION.SDK_INT >= 37) {
                                    TipIconButton(Sym.KEEP, if (pinned) "Stop keeping on top" else "Keep on top", filled = pinned,
                                        tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) { togglePin() }
                                }
                            }
                            MainActivityBridge.EditorPaneFor(session, onMessage)
                        }
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Stay on top by default (Settings › Mini calculator), once per opening.
        if (hasFocus && !autoPinTried && SummaApp.instance.prefs.value.miniOnTop) {
            autoPinTried = true
            window.decorView.postDelayed({ if (!pinned) togglePin(quiet = true) }, 350)
        }
    }

    companion object { var instance: java.lang.ref.WeakReference<MiniActivity> = java.lang.ref.WeakReference(null) }

    /** Back to the big window, on the same sheet, closing the mini one. */
    fun backToBig() {
        session?.saveNow()
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SHEET, sheetId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Already open: "mini" from another big window moves that window's sheet here.
        intent.getStringExtra(MainActivity.EXTRA_SHEET)?.let { sheetId = pickSheet(intent) }
    }

    /** The sheet asked for, else the one you had open last, else the newest one (or a new one). */
    private fun pickSheet(i: Intent?): String {
        val lib = SummaApp.instance.library
        fun ok(id: String?) = id != null && lib.get(id)?.trashedAt == null && lib.get(id) != null
        i?.getStringExtra(MainActivity.EXTRA_SHEET)?.let { if (ok(it)) return it }
        SummaApp.instance.prefs.getString("lastSheet")?.let { if (ok(it)) return it }
        lib.state.value.sheets.filter { it.trashedAt == null && !Library.isSpecial(it.id) }.maxByOrNull { it.updated }?.let { return it.id }
        return lib.create("").id
    }


    private fun togglePin(quiet: Boolean = false) {
        if (Build.VERSION.SDK_INT < 37) return
        val am = getSystemService(ActivityManager::class.java)
        val task = am.appTasks.firstOrNull { it.taskInfo?.taskId == taskId } ?: return
        val want = !pinned
        try {
            task.requestWindowingLayer(
                if (want) ActivityManager.AppTask.WINDOWING_LAYER_PINNED else ActivityManager.AppTask.WINDOWING_LAYER_NORMAL_APP,
                mainExecutor,
                object : OutcomeReceiver<Int, Exception> {
                    override fun onResult(result: Int) {
                        if (result == ActivityManager.AppTask.WINDOWING_LAYER_REQUEST_GRANTED) {
                            pinned = want
                            if (!quiet) onMessage(if (want) "Stays on top of other windows" else "Back to a normal window")
                        } else if (!quiet) onMessage("Android didn't allow keeping this window on top here")
                    }
                    override fun onError(error: Exception) {
                        if (!quiet) onMessage("Couldn't keep on top: ${error.message ?: "not available"}")
                    }
                },
            )
        } catch (e: Throwable) {
            if (!quiet) onMessage("Keeping on top isn't available on this device")
        }
    }
}

/** A no-UI trampoline so launcher shortcuts open the mini window at its small size. */
class MiniLauncher : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MainActivity.openMini(this)
        finish()
    }
}

/** Shares the editor pane with the main window. */
object MainActivityBridge {
    @androidx.compose.runtime.Composable
    fun EditorPaneFor(session: Session, onMessage: (String) -> Unit) {
        EditorPane(session, wide = false, modifier = Modifier.fillMaxSize(), onMessage = onMessage, mini = true)
    }
}
