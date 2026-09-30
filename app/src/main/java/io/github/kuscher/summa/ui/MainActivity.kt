package io.github.kuscher.summa.ui

import android.app.ActivityOptions
import android.app.HandoffActivityData
import android.app.HandoffActivityDataRequestInfo
import android.app.HandoffActivityParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.luminance
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
import io.github.kuscher.summa.SummaApp
import io.github.kuscher.summa.data.Library
import io.github.kuscher.summa.data.SheetMeta
import io.github.kuscher.summa.ui.editor.Display
import io.github.kuscher.summa.ui.editor.DisplayPill
import io.github.kuscher.summa.ui.editor.Keypad
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
import kotlinx.coroutines.flow.SharingStarted
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
        val requested = sheetFromIntent(intent)
        if (Build.VERSION.SDK_INT >= 37) {
            try { setHandoffEnabled(true, HandoffActivityParams.Builder().build()) } catch (_: Throwable) {}
        }
        setContent { App(this, requested) }
    }

    /** Which sheet an intent asks for: a sheet id, shared text, a handoff, or "new sheet". */
    private fun sheetFromIntent(i: Intent?): String? {
        val lib = SummaApp.instance.library
        i ?: return null
        i.getStringExtra(EXTRA_SHEET)?.let { return it }
        i.getStringExtra(HANDOFF_TEXT)?.let { text ->
            val id = i.getStringExtra(HANDOFF_ID)
            return if (id != null && lib.get(id) != null) { lib.save(id, text, null); id } else lib.create(text).id
        }
        if (i.action == Intent.ACTION_SEND) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            val subject = i.getStringExtra(Intent.EXTRA_SUBJECT)
            return lib.create(if (subject != null && !text.trimStart().startsWith("#")) "# $subject\n$text" else text).id
        }
        if (i.action == ACTION_NEW_SHEET) return lib.create("").id
        return null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sheetFromIntent(intent)?.let { DebugHooks.open(it) }
    }

    override fun onResume() { super.onResume(); current = WeakReference(this) }

    override fun onPause() { session?.saveNow(); super.onPause() }

    /** Continue this sheet on another device (Android 17 Handoff). */
    override fun onHandoffActivityDataRequested(info: HandoffActivityDataRequestInfo): HandoffActivityData {
        val s = session
        val extras = PersistableBundle()
        if (s != null) {
            extras.putString(HANDOFF_TEXT, s.state.text.toString().take(60_000))
            extras.putString(HANDOFF_ID, s.id)
        }
        return HandoffActivityData.Builder(ComponentName(this, MainActivity::class.java)).setExtras(extras).build()
    }

    /** Lists Summa's shortcuts in the system Keyboard Shortcuts helper (Meta+/). */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>?, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        val c = KeyEvent.META_CTRL_ON
        data?.add(KeyboardShortcutGroup("Summa", listOf(
            KeyboardShortcutInfo("New sheet", KeyEvent.KEYCODE_N, c),
            KeyboardShortcutInfo("New window", KeyEvent.KEYCODE_N, c or KeyEvent.META_SHIFT_ON),
            KeyboardShortcutInfo("Search sheets", KeyEvent.KEYCODE_K, c),
            KeyboardShortcutInfo("Copy the current answer", KeyEvent.KEYCODE_C, c or KeyEvent.META_SHIFT_ON),
            KeyboardShortcutInfo("Turn lines into notes", KeyEvent.KEYCODE_SLASH, c),
            KeyboardShortcutInfo("Duplicate line", KeyEvent.KEYCODE_D, c),
            KeyboardShortcutInfo("Move line up", KeyEvent.KEYCODE_DPAD_UP, KeyEvent.META_ALT_ON),
            KeyboardShortcutInfo("Move line down", KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.META_ALT_ON),
            KeyboardShortcutInfo("Insert the answer above (prev)", KeyEvent.KEYCODE_BACKSLASH, c),
            KeyboardShortcutInfo("Mini calculator", KeyEvent.KEYCODE_M, c or KeyEvent.META_SHIFT_ON),
            KeyboardShortcutInfo("Show or hide the sheet list", KeyEvent.KEYCODE_B, c),
            KeyboardShortcutInfo("Settings", KeyEvent.KEYCODE_COMMA, c),
        )))
        data?.add(KeyboardShortcutGroup("Autocomplete", listOf(
            KeyboardShortcutInfo("Insert suggestion", KeyEvent.KEYCODE_TAB, 0),
            KeyboardShortcutInfo("Hide suggestions", KeyEvent.KEYCODE_ESCAPE, 0),
        )))
    }

    companion object {
        const val EXTRA_SHEET = "sheet"
        const val ACTION_NEW_SHEET = "io.github.kuscher.summa.NEW_SHEET"
        private const val HANDOFF_TEXT = "summa.handoff.text"
        private const val HANDOFF_ID = "summa.handoff.id"
        var current: WeakReference<MainActivity> = WeakReference(null)

        /** Opens [sheet] in a window of its own. */
        fun openInNewWindow(context: Context, sheet: String?) {
            val i = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            if (sheet != null) i.putExtra(EXTRA_SHEET, sheet)
            context.startActivity(i)
        }

        /** Opens the mini calculator, bottom-right of the screen. */
        fun openMini(context: Context) {
            val i = Intent(context, MiniActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val dm = context.resources.displayMetrics
            val w = (380 * dm.density).toInt(); val h = (460 * dm.density).toInt()
            val m = (24 * dm.density).toInt()
            val opts = ActivityOptions.makeBasic().setLaunchBounds(Rect(dm.widthPixels - w - m, dm.heightPixels - h - m * 3, dm.widthPixels - m, dm.heightPixels - m * 3))
            try { context.startActivity(i, opts.toBundle()) } catch (_: Exception) { context.startActivity(i) }
        }
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
                ?: index.sheets.filter { it.trashedAt == null && it.id != Library.SCRATCH }.maxByOrNull { it.updated }?.id)
        }
        var screen by rememberSaveable { mutableStateOf("sheet") }
        var showList by rememberSaveable { mutableStateOf(false) }
        val snack = remember { SnackbarHostState() }
        val search = rememberTextFieldState()
        val searchFocus = remember { FocusRequester() }
        val engineSettings = remember { app.prefs.state.map { app.prefs.engineSettings(it) }.stateIn(scope, SharingStarted.Eagerly, app.prefs.engineSettings()) }
        val surface = MaterialTheme.colorScheme.surfaceContainerLow
        SideEffect { Caption.enable(activity, surface.luminance() > 0.5f) }

        val id = currentId?.takeIf { cid -> index.sheets.any { it.id == cid && it.trashedAt == null } }
        val session = remember(id) { id?.let { Session(it, app.library.text(it), app.library, engineSettings, app.rates.rates, scope) } }
        DisposableEffect(session) {
            activity.session = session
            onDispose { session?.close() }
        }
        LaunchedEffect(id) { if (id != null) app.prefs.putString("lastSheet", id) }

        fun open(sheet: String) { currentId = sheet; screen = "sheet"; showList = false; search.setTextAndPlaceCursorAtEnd("") }
        fun newSheet() { open(app.library.create("").id) }
        fun message(m: String) { scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(m) } }

        DebugHooks.open = { open(it) }
        DebugHooks.newSheet = { newSheet() }
        DebugHooks.screen = { screen = it }

        Surface(color = MaterialTheme.colorScheme.surface) {
            BoxWithConstraints(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val ctrl = e.isCtrlPressed
                        when {
                            ctrl && e.isShiftPressed && e.key == Key.N -> { MainActivity.openInNewWindow(activity, null); true }
                            ctrl && e.key == Key.N -> { newSheet(); true }
                            ctrl && e.key == Key.K -> { runCatching { searchFocus.requestFocus() }; showList = true; true }
                            ctrl && e.key == Key.Comma -> { screen = "settings"; true }
                            ctrl && e.isShiftPressed && e.key == Key.M -> { MainActivity.openMini(activity); true }
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
                val cap = rememberCaptionInsets()
                val meta = index.sheets.firstOrNull { it.id == id }
                val sides = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                val topInsets = if (cap.present) WindowInsets(0) else WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top)
                Box(Modifier.fillMaxSize().windowInsetsPadding(sides).windowInsetsPadding(topInsets)) {
                    if (wide) {
                        Column(Modifier.fillMaxSize()) {
                            // On a Googlebook this header lives in the window's caption bar.
                            HeaderRow(cap, wideLayout = true) {
                                if (screen == "settings") {
                                    HeaderButton(Sym.ARROW_BACK, "Back to the sheet", cap.present) { screen = "sheet" }
                                    Text("Settings", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(700)))
                                } else {
                                    SheetHeader(session, meta, onBack = null, onToggleSidebar = { app.prefs.update { it.copy(sidebar = !it.sidebar) } },
                                        onSettings = { screen = "settings" }, onMessage = ::message, inCaption = cap.present,
                                        onNewWindow = { MainActivity.openInNewWindow(activity, it) }, onMini = { MainActivity.openMini(activity) })
                                }
                            }
                            Row(Modifier.weight(1f)) {
                                AnimatedVisibility(settings.sidebar, enter = expandHorizontally(), exit = shrinkHorizontally()) {
                                    Sidebar(app.library, index, id, search, searchFocus, ::open, ::newSheet, onSettings = { screen = "settings" },
                                        onMini = { MainActivity.openMini(activity) }, onNewWindow = { MainActivity.openInNewWindow(activity, it) },
                                        modifier = Modifier.width(272.dp))
                                }
                                val mod = Modifier.weight(1f)
                                when {
                                    screen == "settings" -> SettingsScreen(onBack = { screen = "sheet" }, modifier = mod, showHeader = false)
                                    session == null -> EmptyState(mod) { newSheet() }
                                    else -> EditorPane(session, true, mod, onMessage = ::message)
                                }
                            }
                        }
                    } else {
                        BackHandler(enabled = !showList && screen == "sheet") { showList = true }
                        BackHandler(enabled = screen == "settings") { screen = "sheet" }
                        when {
                            screen == "settings" -> SettingsScreen(onBack = { screen = "sheet" })
                            showList || session == null -> LibraryScreen(app.library, index, id, search, searchFocus, ::open, ::newSheet, onSettings = { screen = "settings" })
                            else -> Column(Modifier.fillMaxSize()) {
                                HeaderRow(cap, wideLayout = false) {
                                    SheetHeader(session, meta, onBack = { showList = true }, onToggleSidebar = null, onSettings = { screen = "settings" },
                                        onMessage = ::message, inCaption = cap.present, onNewWindow = null, onMini = null)
                                }
                                EditorPane(session, false, Modifier.weight(1f), onMessage = ::message)
                            }
                        }
                    }
                    SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) { d ->
                        Snackbar(d, shape = RoundedCornerShape(16.dp))
                    }
                }
            }
        }
    }
}

/** The top row; in a desktop window it fills the caption bar and keeps clear of the system's controls. */
@Composable
fun HeaderRow(cap: CaptionInsets, wideLayout: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .height(if (cap.present) maxOf(cap.height, 44.dp) else 56.dp)
            .background(if (wideLayout) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface)
            .padding(start = cap.start + 6.dp, end = cap.end + 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
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

/** A round icon button; inside the caption bar it opts out of window dragging. */
@Composable
fun HeaderButton(sym: String, label: String, inCaption: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).let { if (inCaption) it.systemGestureExclusion() else it }.clip(CircleShape)
            .clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { SymIcon(sym, size = 21.sp, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** Sheet title, rates chip, mini, share and the More menu. */
@Composable
fun RowScope.SheetHeader(
    session: Session?, meta: SheetMeta?, onBack: (() -> Unit)?, onToggleSidebar: (() -> Unit)?, onSettings: () -> Unit,
    onMessage: (String) -> Unit, inCaption: Boolean, onNewWindow: ((String) -> Unit)?, onMini: (() -> Unit)?,
) {
    val app = SummaApp.instance
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val ex = if (inCaption) Modifier.systemGestureExclusion() else Modifier
    when {
        onBack != null -> HeaderButton(Sym.ARROW_BACK, "Sheets", inCaption, onBack)
        onToggleSidebar != null -> HeaderButton(Sym.MENU, "Show or hide the sheet list", inCaption, onToggleSidebar)
    }
    SymIcon(iconFor(meta?.title ?: ""), size = 19.sp, filled = true, tint = scheme.primary, modifier = Modifier.padding(start = 6.dp))
    Text(
        meta?.title?.ifBlank { "Untitled" } ?: "Summa", Modifier.padding(start = 10.dp).weight(1f, fill = false),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(700)), maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.weight(1f))
    if (session == null) return
    val ev = session.evaluated
    if (ev?.result?.anyRateDependent == true) {
        val r by app.rates.rates.collectAsState()
        Row(
            ex.padding(end = 6.dp).clip(CircleShape).background(scheme.surfaceContainerHigh)
                .clickable { app.rates.refresh(force = true); onMessage("Updating exchange rates…") }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics { contentDescription = "Exchange rates from ${r.source}, ${r.asOf}. Click to update." },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymIcon(Sym.CURRENCY_EXCHANGE, size = 16.sp, tint = scheme.tertiary)
            Text("${if (r.source.contains("Central")) "ECB" else r.source.ifBlank { "Rates" }} · ${prettyDate(r.asOf)}",
                Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
    }
    var more by remember { mutableStateOf(false) }
    val files = rememberFileActions(session, onMessage) { DebugHooks.open(it) }
    if (onMini != null) HeaderButton(Sym.PICTURE_IN_PICTURE_ALT, "Mini calculator", inCaption, onMini)
    HeaderButton(Sym.IOS_SHARE, "Share", inCaption) { shareSheet(context, session) }
    Box {
        HeaderButton(Sym.MORE_VERT, "More", inCaption) { more = true }
        DropdownMenu(more, onDismissRequest = { more = false }, shape = RoundedCornerShape(18.dp)) {
            DropdownMenuItem(text = { Text(if (meta?.pinned == true) "Unpin" else "Pin") }, leadingIcon = { SymIcon(Sym.KEEP, size = 20.sp) },
                onClick = { more = false; app.library.pin(session.id, meta?.pinned != true) })
            if (onNewWindow != null) DropdownMenuItem(text = { Text("Open in new window") }, leadingIcon = { SymIcon(Sym.OPEN_IN_NEW, size = 20.sp) },
                onClick = { more = false; onNewWindow(session.id) })
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
            DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { SymIcon(Sym.SETTINGS, size = 20.sp) }, onClick = { more = false; onSettings() })
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

/** Keypad backspace: deletes the selection or the character before the cursor. */
fun backspace(s: Session) {
    s.state.edit {
        val sel = selection
        if (!sel.collapsed) { replace(sel.min, sel.max, ""); selection = TextRange(sel.min) }
        else if (sel.min > 0) { replace(sel.min - 1, sel.min, ""); selection = TextRange(sel.min - 1) }
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

/** The editor with its floating toolbar (and, on phones, the keypad). */
@Composable
fun EditorPane(session: Session, wide: Boolean, modifier: Modifier, onMessage: (String) -> Unit, mini: Boolean = false) {
    val app = SummaApp.instance
    val settings by app.prefs.state.collectAsState()
    val scroll = rememberScrollState()
    val text = session.state.text
    val starts = remember(text.toString()) { lineStarts(text) }
    val sel = session.state.selection
    val a = lineOf(starts, sel.min)
    val b = lineOf(starts, if (sel.collapsed) sel.min else maxOf(sel.min, sel.max - 1))
    var statIdx by remember { mutableIntStateOf(0) }
    var keypad by rememberSaveable { mutableStateOf(false) }
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
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().let { if (keypad) it else it.imePadding() }) {
            SheetEditor(
                session, settings, activeLine = a, scroll = scroll, compact = !wide || mini,
                contentPadding = PaddingValues(top = 6.dp, bottom = if (mini) 76.dp else 120.dp),
                onInsert = { insertAtCursor(session, it) }, onMessage = onMessage, keypadOpen = keypad,
            )
            if (mini) {
                DisplayPill(
                    display, onCycle = { statIdx++ }, big = false,
                    onCopy = { v -> copyToClipboard(app, v); onMessage("Copied $v") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 12.dp),
                )
            } else {
                SheetToolbar(
                    display, onInsert = { insertAtCursor(session, it) }, onCycle = { statIdx++ }, onMessage = onMessage,
                    keypadOpen = keypad, onKeypad = if (!wide) ({ keypad = !keypad }) else null,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
            }
        }
        if (keypad && !wide && !mini) Keypad(
            onText = { insertAtCursor(session, it) }, onBackspace = { backspace(session) },
            onKeyboard = { keypad = false },
        )
    }
}

fun prettyDate(iso: String?): String = try {
    val d = java.time.LocalDate.parse(iso)
    d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
} catch (_: Exception) { iso ?: "" }

/** "Flights: 2 × €189   = €378.00" per line, for sharing and "copy lines and answers". */
fun withAnswers(s: Session): String {
    val ev = s.evaluated
    val lines = s.state.text.toString().split('\n')
    val width = (lines.maxOfOrNull { it.length } ?: 0).coerceAtMost(48)
    return lines.mapIndexed { i, l ->
        val a = ev?.result?.lines?.getOrNull(i)?.answer
        if (a == null) l else l.padEnd(width) + "   = " + a
    }.joinToString("\n")
}

fun shareSheet(context: Context, s: Session) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, withAnswers(s))
        putExtra(Intent.EXTRA_SUBJECT, Library.titleOf(s.state.text.toString()))
    }
    context.startActivity(Intent.createChooser(send, "Share sheet"))
}

/** Hooks the debug receiver uses to drive the UI in tests (DUMP-guarded). */
object DebugHooks {
    var open: (String) -> Unit = {}
    var newSheet: () -> Unit = {}
    var screen: (String) -> Unit = {}
    var focus: () -> Unit = {}
}
