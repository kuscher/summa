package io.github.kuscher.summa.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.github.kuscher.summa.SummaApp
import io.github.kuscher.summa.ui.editor.Display
import io.github.kuscher.summa.ui.editor.Session
import io.github.kuscher.summa.ui.editor.SheetEditor
import io.github.kuscher.summa.ui.editor.SheetToolbar
import io.github.kuscher.summa.ui.editor.lineOf
import io.github.kuscher.summa.ui.editor.lineStarts
import io.github.kuscher.summa.ui.library.LibraryScreen
import io.github.kuscher.summa.ui.library.Sidebar
import io.github.kuscher.summa.ui.library.iconFor
import io.github.kuscher.summa.ui.settings.SettingsScreen
import io.github.kuscher.summa.ui.theme.SummaTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {
    /** The open sheet, for the debug hooks and window-level actions. */
    var session: Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        val requested = intent?.getStringExtra(EXTRA_SHEET)
        setContent { App(this, requested) }
    }

    override fun onResume() { super.onResume(); current = WeakReference(this) }

    override fun onPause() { session?.saveNow(); super.onPause() }

    companion object {
        const val EXTRA_SHEET = "sheet"
        var current: WeakReference<MainActivity> = WeakReference(null)
    }
}

private val STATS = listOf("sum", "avg", "count", "min", "max")
private val STAT_LABEL = mapOf("sum" to "sum", "avg" to "average", "count" to "count", "min" to "min", "max" to "max")

@Composable
fun App(activity: MainActivity, requested: String?) {
    val app = SummaApp.instance
    val settings by app.prefs.state.collectAsState()
    val index by app.library.state.collectAsState()
    val scope = rememberCoroutineScope()
    SummaTheme(settings) {
        var currentId by rememberSaveable {
            mutableStateOf(requested ?: app.prefs.getString("lastSheet")?.takeIf { id -> index.sheets.any { it.id == id && it.trashedAt == null } }
                ?: index.sheets.filter { it.trashedAt == null }.maxByOrNull { it.updated }?.id)
        }
        var screen by rememberSaveable { mutableStateOf("sheet") }
        var showList by rememberSaveable { mutableStateOf(false) }
        val snack = remember { SnackbarHostState() }
        val search = rememberTextFieldState()
        val searchFocus = remember { FocusRequester() }
        val engineSettings = remember { app.prefs.state.map { app.prefs.engineSettings(it) }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, app.prefs.engineSettings()) }

        // The current sheet gets a session; switching sheets saves and closes the old one.
        val id = currentId?.takeIf { cid -> index.sheets.any { it.id == cid && it.trashedAt == null } }
        val session = remember(id) { id?.let { Session(it, app.library.text(it), app.library, engineSettings, app.rates.rates, scope) } }
        DisposableEffect(session) {
            activity.session = session
            onDispose { session?.close() }
        }
        LaunchedEffect(id) { app.prefs.putString("lastSheet", id) }

        fun open(sheet: String) { currentId = sheet; screen = "sheet"; showList = false; search.setTextAndPlaceCursorAtEnd("") }
        fun newSheet() { open(app.library.create("").id) }
        fun message(m: String) { scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(m) } }

        DebugHooks.open = { open(it) }
        DebugHooks.newSheet = { newSheet() }
        DebugHooks.screen = { screen = it }

        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val ctrl = e.isCtrlPressed
                    when {
                        ctrl && e.key == Key.N -> { newSheet(); true }
                        ctrl && e.key == Key.K -> { runCatching { searchFocus.requestFocus() }; showList = true; true }
                        ctrl && e.key == Key.Comma -> { screen = "settings"; true }
                        ctrl && e.isShiftPressed && e.key == Key.C -> {
                            val s = session ?: return@onPreviewKeyEvent false
                            val ans = currentAnswer(s)
                            if (ans != null) { copyToClipboard(activity, ans); message("Copied $ans") }
                            true
                        }
                        ctrl && e.key == Key.Slash -> { session?.let { toggleComment(it) }; true }
                        ctrl && e.key == Key.D -> { session?.let { duplicateLine(it) }; true }
                        ctrl && e.key == Key.Backslash -> { session?.let { insertAtCursor(it, "prev") }; true }
                        e.isAltPressed && (e.key == Key.DirectionUp || e.key == Key.DirectionDown) -> {
                            session?.let { moveLine(it, if (e.key == Key.DirectionUp) -1 else 1) }; true
                        }
                        ctrl && e.key == Key.B -> { app.prefs.update { it.copy(sidebar = !it.sidebar) }; true }
                        else -> false
                    }
                },
        ) {
            val wide = maxWidth >= 720.dp
            val content: @Composable (Modifier) -> Unit = { mod ->
                when {
                    screen == "settings" -> SettingsScreen(onBack = { screen = "sheet" }, modifier = mod)
                    session == null -> EmptyState(mod) { newSheet() }
                    else -> EditorPane(session, wide, mod, onBack = if (wide) null else ({ showList = true }), onMessage = ::message,
                        onToggleSidebar = if (wide) ({ app.prefs.update { it.copy(sidebar = !it.sidebar) } }) else null,
                        onSettings = { screen = "settings" })
                }
            }
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        AnimatedVisibility(settings.sidebar, enter = expandHorizontally(), exit = shrinkHorizontally()) {
                            Sidebar(app.library, index, id, search, searchFocus, ::open, ::newSheet, onSettings = { screen = "settings" },
                                onMini = null, onNewWindow = null, modifier = Modifier.width(272.dp))
                        }
                        content(Modifier.weight(1f))
                    }
                } else {
                    BackHandler(enabled = !showList && screen == "sheet") { showList = true }
                    BackHandler(enabled = screen == "settings") { screen = "sheet" }
                    if (showList && screen == "sheet") LibraryScreen(app.library, index, id, search, searchFocus, ::open, ::newSheet, onSettings = { screen = "settings" })
                    else content(Modifier.fillMaxSize())
                }
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) { d ->
                    Snackbar(d, shape = RoundedCornerShape(16.dp))
                }
            }
        }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onNew: () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No sheet open", style = MaterialTheme.typography.headlineSmall)
            Text("Start a new sheet, or pick one from the list.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.Button(onClick = onNew, modifier = Modifier.padding(top = 16.dp)) { Text("New sheet") }
        }
    }
}

/** The answer on the cursor's line. */
fun currentAnswer(s: Session): String? {
    val ev = s.evaluated ?: return null
    val text = s.state.text
    val line = lineOf(lineStarts(text), s.state.selection.min)
    return ev.result.lines.getOrNull(line)?.answer
}

fun insertAtCursor(s: Session, text: String) {
    s.state.edit {
        val sel = selection
        replace(sel.min, sel.max, text)
        selection = TextRange(sel.min + text.length)
    }
}

private fun lineRange(s: Session): IntRange {
    val t = s.state.text
    val starts = lineStarts(t)
    val a = lineOf(starts, s.state.selection.min)
    val b = lineOf(starts, maxOf(s.state.selection.min, s.state.selection.max - if (s.state.selection.collapsed) 0 else 1))
    return a..b
}

fun toggleComment(s: Session) {
    val lines = s.state.text.toString().split('\n').toMutableList()
    val r = lineRange(s)
    val allCommented = r.all { lines[it].trimStart().startsWith("//") }
    for (i in r) lines[i] = if (allCommented) lines[i].replaceFirst(Regex("^(\\s*)// ?"), "$1") else "// " + lines[i]
    val sel = s.state.selection
    s.state.edit { replace(0, length, lines.joinToString("\n")); selection = TextRange(minOf(sel.min, length)) }
}

fun duplicateLine(s: Session) {
    val t = s.state.text.toString()
    val starts = lineStarts(t)
    val i = lineOf(starts, s.state.selection.min)
    val start = starts[i]
    val end = starts.getOrNull(i + 1)?.minus(1) ?: t.length
    val line = t.substring(start, end)
    val caret = end + 1 + (s.state.selection.min - start)
    s.state.edit { replace(end, end, "\n" + line); selection = TextRange(caret) }
}

fun moveLine(s: Session, dir: Int) {
    val lines = s.state.text.toString().split('\n').toMutableList()
    val starts = lineStarts(s.state.text)
    val i = lineOf(starts, s.state.selection.min)
    val j = i + dir
    if (j !in lines.indices) return
    val col = s.state.selection.min - starts[i]
    val tmp = lines[i]; lines[i] = lines[j]; lines[j] = tmp
    val text = lines.joinToString("\n")
    val newStart = lineStarts(text)[j]
    s.state.edit { replace(0, length, text); selection = TextRange(newStart + minOf(col, lines[j].length)) }
}

@Composable
fun EditorPane(
    session: Session, wide: Boolean, modifier: Modifier, onBack: (() -> Unit)?, onMessage: (String) -> Unit,
    onToggleSidebar: (() -> Unit)?, onSettings: () -> Unit,
) {
    val app = SummaApp.instance
    val settings by app.prefs.state.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val index by app.library.state.collectAsState()
    val meta = index.sheets.firstOrNull { it.id == session.id }
    val scroll = rememberScrollState()
    val text = session.state.text
    val starts = remember(text.toString()) { lineStarts(text) }
    val sel = session.state.selection
    val a = lineOf(starts, sel.min)
    val b = lineOf(starts, if (sel.collapsed) sel.min else maxOf(sel.min, sel.max - 1))
    var statIdx by remember { mutableIntStateOf(0) }
    val ev = session.evaluated
    val display = run {
        if (ev == null) Display("Line ${a + 1}", null, false)
        else if (a == b) Display("Line ${a + 1}", ev.result.lines.getOrNull(a)?.answer, false)
        else {
            val kind = STATS[statIdx % STATS.size]
            val v = ev.result.stat(kind, (a..b).toList())
            Display("${b - a + 1} lines · ${STAT_LABEL[kind]}", v?.let { ev.result.format(it) }, true)
        }
    }
    val context = LocalContext.current
    Column(modifier.fillMaxSize()) {
        // Title row (on a Googlebook this sits under the window's caption bar).
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                onBack != null -> RoundIcon(Sym.ARROW_BACK, "Sheets", onBack)
                onToggleSidebar != null -> RoundIcon(Sym.MENU, "Show or hide the sheet list", onToggleSidebar)
            }
            SymIcon(iconFor(meta?.title ?: ""), size = 20.sp, filled = true, tint = scheme.primary, modifier = Modifier.padding(start = 6.dp))
            Text(
                meta?.title?.ifBlank { "Untitled" } ?: "Untitled", Modifier.weight(1f).padding(start = 10.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(700)), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            var more by remember { mutableStateOf(false) }
            val files = rememberFileActions(session, onMessage) { SummaApp.instance.prefs.putString("lastSheet", it); DebugHooks.open(it) }
            RoundIcon(Sym.IOS_SHARE, "Share") { shareSheet(context, session) }
            Box {
                RoundIcon(Sym.MORE_VERT, "More") { more = true }
                DropdownMenu(more, onDismissRequest = { more = false }, shape = RoundedCornerShape(18.dp)) {
                    DropdownMenuItem(text = { Text(if (meta?.pinned == true) "Unpin" else "Pin") }, leadingIcon = { SymIcon(Sym.KEEP, size = 20.sp) },
                        onClick = { more = false; app.library.pin(session.id, meta?.pinned != true) })
                    DropdownMenuItem(text = { Text("Copy lines and answers") }, leadingIcon = { SymIcon(Sym.CONTENT_COPY, size = 20.sp) },
                        onClick = { more = false; copyToClipboard(context, withAnswers(session)); onMessage("Copied the sheet with its answers") })
                    DropdownMenuItem(text = { Text("Export as text…") }, leadingIcon = { SymIcon(Sym.FILE_DOWNLOAD, size = 20.sp) },
                        onClick = { more = false; files.exportText() })
                    DropdownMenuItem(text = { Text("Export with answers (Markdown)…") }, leadingIcon = { SymIcon(Sym.FILE_DOWNLOAD, size = 20.sp) },
                        onClick = { more = false; files.exportMarkdown() })
                    DropdownMenuItem(text = { Text("Import sheets…") }, leadingIcon = { SymIcon(Sym.FILE_UPLOAD, size = 20.sp) },
                        onClick = { more = false; files.import() })
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(text = { Text("Move to trash") }, leadingIcon = { SymIcon(Sym.DELETE, size = 20.sp) },
                        onClick = { more = false; app.library.trash(session.id) })
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { SymIcon(Sym.SETTINGS, size = 20.sp) }, onClick = { more = false; onSettings() })
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
            SheetEditor(
                session, settings, activeLine = a, scroll = scroll, compact = !wide,
                contentPadding = PaddingValues(top = 6.dp, bottom = 120.dp),
                onInsert = { insertAtCursor(session, it) }, onMessage = onMessage,
            )
            SheetToolbar(
                display, onInsert = { insertAtCursor(session, it) }, onCycle = { statIdx++ }, onMessage = onMessage,
                keypadOpen = false, onKeypad = null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
fun RoundIcon(sym: String, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        SymIcon(sym, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Flights: 2 × €189   €378.00" per line, for sharing and "copy lines and answers". */
fun withAnswers(s: Session): String {
    val ev = s.evaluated
    val lines = s.state.text.toString().split('\n')
    val width = (lines.maxOfOrNull { it.length } ?: 0).coerceAtMost(48)
    return lines.mapIndexed { i, l ->
        val a = ev?.result?.lines?.getOrNull(i)?.answer
        if (a == null) l else l.padEnd(width) + "   = " + a
    }.joinToString("\n")
}

fun shareSheet(context: android.content.Context, s: Session) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, withAnswers(s))
        putExtra(Intent.EXTRA_SUBJECT, io.github.kuscher.summa.data.Library.titleOf(s.state.text.toString()))
    }
    context.startActivity(Intent.createChooser(send, "Share sheet"))
}

/** Hooks the debug receiver uses to drive the UI in tests (debug builds; DUMP-guarded). */
object DebugHooks {
    var open: (String) -> Unit = {}
    var newSheet: () -> Unit = {}
    var screen: (String) -> Unit = {}
}

