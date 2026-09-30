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
import androidx.compose.foundation.systemGestureExclusion
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The mini calculator: one scratch sheet in a small window. On Android 17 desktops it can stay
 * above other windows (the pinned windowing layer); the system parks a pinned window bottom-right.
 */
class MiniActivity : ComponentActivity() {
    private var pinned by mutableStateOf(false)
    private var autoPinTried = false
    private var onMessage: (String) -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        instance = java.lang.ref.WeakReference(this)
        val app = SummaApp.instance
        app.library.scratch()
        setContent {
            val settings by app.prefs.state.collectAsState()
            val scope = rememberCoroutineScope()
            SummaTheme(settings) {
                val snack = remember { SnackbarHostState() }
                onMessage = { m -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(m) } }
                val engineSettings = remember { app.prefs.state.map { app.prefs.engineSettings(it) }.stateIn(scope, SharingStarted.Eagerly, app.prefs.engineSettings()) }
                val session = remember { Session(Library.SCRATCH, app.library.text(Library.SCRATCH), app.library, engineSettings, app.rates.rates, scope) }
                DisposableEffect(session) { onDispose { session.close() } }
                val bar = MaterialTheme.colorScheme.surfaceContainerLow
                SideEffect { Caption.enable(this, bar.luminance() > 0.5f) }
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
                        Column(Modifier.fillMaxSize()) {
                            val cap = rememberCaptionInsets()
                            HeaderRow(cap, wideLayout = true) {
                                Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SymIcon(Sym.CALCULATE, size = 19.sp, filled = true, tint = MaterialTheme.colorScheme.primary)
                                    Text("Mini", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight(700)))
                                }
                                Spacer(Modifier.weight(1f))
                                if (Build.VERSION.SDK_INT >= 37) {
                                    Box(
                                        Modifier.size(38.dp).let { if (cap.present) it.systemGestureExclusion() else it }.clip(if (pinned) RoundedCornerShape(12.dp) else CircleShape)
                                            .background(if (pinned) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                                            .clickable { togglePin() }
                                            .semantics { contentDescription = if (pinned) "Stop keeping on top" else "Keep on top" },
                                        contentAlignment = Alignment.Center,
                                    ) { SymIcon(Sym.KEEP, size = 20.sp, filled = pinned, tint = if (pinned) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                HeaderButton(Sym.OPEN_IN_FULL, "Open in Summa", cap.present) {
                                    val text = session.state.text.toString()
                                    session.saveNow()
                                    val sheet = app.library.create(text.lines().filterNot { it.startsWith("// Mini calculator") }.joinToString("\n").trim().ifEmpty { "" })
                                    startActivity(Intent(this@MiniActivity, MainActivity::class.java).putExtra(MainActivity.EXTRA_SHEET, sheet.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                            MainActivityBridge.EditorPaneFor(session, onMessage)
                        }
                        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)) { d -> Snackbar(d, shape = RoundedCornerShape(14.dp)) }
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
