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
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import io.github.kuscher.summa.ui.editor.Session
import io.github.kuscher.summa.ui.editor.SheetEditor
import io.github.kuscher.summa.ui.editor.lineOf
import io.github.kuscher.summa.ui.editor.lineStarts
import io.github.kuscher.summa.ui.library.PhoneList
import io.github.kuscher.summa.ui.library.SheetActions
import io.github.kuscher.summa.ui.library.Sidebar
import kotlinx.coroutines.delay
import io.github.kuscher.summa.ui.settings.SettingsScreen
import io.github.kuscher.summa.ui.theme.SummaTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {
    /** The open sheet, for the debug hooks and window-level actions. */
    var session: Session? = null
    lateinit var captionTracker: CaptionTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        captionTracker = CaptionTracker(this)
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
    @androidx.annotation.RequiresApi(37)
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
            KeyboardShortcutInfo("Print or save as PDF", KeyEvent.KEYCODE_P, c),
        )))
        data?.add(KeyboardShortcutGroup("Suggestions (grey text after the cursor)", listOf(
            KeyboardShortcutInfo("Accept the suggestion", KeyEvent.KEYCODE_TAB, 0),
            KeyboardShortcutInfo("Accept the suggestion", KeyEvent.KEYCODE_DPAD_RIGHT, 0),
            KeyboardShortcutInfo("Hide the suggestion", KeyEvent.KEYCODE_ESCAPE, 0),
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

        /** From a big window: the sheet moves to the mini calculator and this window closes (the mini can bring it back). */
        fun switchToMini(activity: MainActivity) {
            activity.session?.saveNow()
            openMini(activity, activity.session?.id)
            activity.finishAndRemoveTask()
        }

        /** Opens the mini calculator on [sheet] (or the sheet you had open last), bottom-right of the screen. */
        fun openMini(context: Context, sheet: String? = null) {
            val i = Intent(context, MiniActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (sheet != null) i.putExtra(EXTRA_SHEET, sheet)
            val dm = context.resources.displayMetrics
            val w = (380 * dm.density).toInt(); val h = (460 * dm.density).toInt()
            val m = (24 * dm.density).toInt()
            val opts = ActivityOptions.makeBasic().setLaunchBounds(Rect(dm.widthPixels - w - m, dm.heightPixels - h - m * 3, dm.widthPixels - m, dm.heightPixels - m * 3))
            try { context.startActivity(i, opts.toBundle()) } catch (_: Exception) { context.startActivity(i) }
        }
    }
}

@Composable
fun App(activity: MainActivity, requested: String?) {
    val app = SummaApp.instance
    val settings by app.prefs.state.collectAsState()
    val index by app.library.state.collectAsState()
    val scope = rememberCoroutineScope()
    SummaTheme(settings) {
        fun live() = index.sheets.filter { it.trashedAt == null && !Library.isSpecial(it.id) }
        var currentId by rememberSaveable {
            mutableStateOf(requested ?: app.prefs.getString("lastSheet")?.takeIf { id -> index.sheets.any { it.id == id && it.trashedAt == null } }
                ?: live().maxByOrNull { it.updated }?.id)
        }
        var screen by rememberSaveable { mutableStateOf("sheet") }
        var showList by rememberSaveable { mutableStateOf(false) }
        var status by remember { mutableStateOf<String?>(null) }
        var deleted by remember { mutableStateOf<SheetMeta?>(null) }
        val search = rememberTextFieldState()
        val searchFocus = remember { FocusRequester() }
        val editorFocus = remember { FocusRequester() }
        // Esc in the search field: back to the sheet.
        val leaveSearch = { runCatching { editorFocus.requestFocus() }; Unit }
        val sidebarShown = settings.sidebar
        val chrome = MaterialTheme.colorScheme.surfaceContainerLow
        SideEffect { Caption.enable(activity, chrome.luminance() > 0.5f) }

        val id = currentId?.takeIf { cid -> index.sheets.any { it.id == cid && it.trashedAt == null } }
        val session = remember(id) { id?.let { app.sessions.acquire(it) } }
        DisposableEffect(session) {
            activity.session = session
            onDispose { session?.let { app.sessions.release(it) } }
        }
        LaunchedEffect(id) { if (id != null) app.prefs.putString("lastSheet", id) }

        fun open(sheet: String) { currentId = sheet; screen = "sheet"; showList = false; search.setTextAndPlaceCursorAtEnd("") }
        fun newSheet() { open(app.library.create("").id) }
        // Feedback is a few words in the title bar, never a pop-up over the sheet.
        fun message(m: String) { status = m; scope.launch { delay(2600); if (status == m) status = null } }
        fun delete(sheet: String) {
            val meta = app.library.get(sheet) ?: return
            app.library.trash(sheet)
            deleted = meta
            if (currentId == sheet) {
                val next = live().filter { it.id != sheet }.maxByOrNull { it.updated }?.id
                currentId = next
                if (next == null || !activity.resources.configuration.let { it.screenWidthDp >= 720 }) showList = true
            }
            scope.launch { delay(10_000); if (deleted?.id == meta.id) deleted = null }
        }
        fun undoDelete() { deleted?.let { app.library.restore(it.id); open(it.id) }; deleted = null }
        val actions = SheetActions(
            open = ::open,
            newWindow = { MainActivity.openInNewWindow(activity, it) },
            duplicate = { open(app.library.duplicate(it).id) },
            delete = ::delete,
        )

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
                            ctrl && e.key == Key.K -> { if (!settings.sidebar) app.prefs.update { it.copy(sidebar = true) }; runCatching { searchFocus.requestFocus() }; showList = true; true }
                            ctrl && e.key == Key.Comma -> { screen = "settings"; true }
                            ctrl && e.isShiftPressed && e.key == Key.M -> { MainActivity.switchToMini(activity); true }
                            ctrl && e.isShiftPressed && e.key == Key.C -> {
                                val s = session ?: return@onPreviewKeyEvent false
                                val ans = currentAnswer(s)
                                if (ans != null) { copyToClipboard(activity, ans); s.flashCopied(lineOf(lineStarts(s.state.text), s.state.selection.min)) }
                                true
                            }
                            ctrl && e.key == Key.Slash -> { session?.let { toggleComment(it) }; true }
                            ctrl && e.key == Key.D -> { session?.let { duplicateLine(it) }; true }
                            ctrl && e.key == Key.Backslash -> { session?.let { insertAtCursor(it, "prev") }; true }
                            e.isAltPressed && (e.key == Key.DirectionUp || e.key == Key.DirectionDown) -> {
                                session?.let { moveLine(it, if (e.key == Key.DirectionUp) -1 else 1) }; true
                            }
                            ctrl && e.key == Key.B -> { app.prefs.update { it.copy(sidebar = !it.sidebar) }; true }
                            // Esc closes Settings (menus and dialogs handle their own Esc first).
                            e.key == Key.Escape && screen == "settings" -> { screen = "sheet"; true }
                            ctrl && e.key == Key.P -> { session?.let { Export.print(activity, SheetSnapshot.of(it)) }; true }
                            else -> false
                        }
                    },
            ) {
                val wide = maxWidth >= 720.dp
                val cap = rememberCaptionInsets(activity.captionTracker)
                val meta = index.sheets.firstOrNull { it.id == id }
                val sides = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                val topInsets = if (cap.present) WindowInsets(0) else WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top)
                Box(Modifier.fillMaxSize().windowInsetsPadding(sides).windowInsetsPadding(topInsets)) {
                    if (wide) {
                        Column(Modifier.fillMaxSize()) {
                            // The system's own title bar stays (app handle, window buttons, dragging);
                            // we only paint it the header's colour, and the header sits right below
                            // it, so the two read as one taller bar.
                            val barColor = if (sidebarShown || screen == "settings") chrome else MaterialTheme.colorScheme.surface
                            CaptionSpacer(cap, barColor)
                            HeaderRow(barColor) {
                                if (screen == "settings") {
                                    HeaderButton(Sym.ARROW_BACK, "Back to the sheet") { screen = "sheet" }
                                    Text("Settings", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(650)))
                                } else {
                                    SheetHeader(session, meta, status, onBack = null, onToggleSidebar = { app.prefs.update { it.copy(sidebar = !it.sidebar) } },
                                        onSettings = { screen = "settings" }, onMessage = ::message,
                                        onNew = ::newSheet, onDelete = ::delete,
                                        onNewWindow = { MainActivity.openInNewWindow(activity, it) }, onMini = { MainActivity.switchToMini(activity) })
                                }
                            }
                            Row(Modifier.weight(1f)) {
                                if (sidebarShown) Sidebar(app.library, index, id, search, searchFocus, actions, ::newSheet, deleted, ::undoDelete, Modifier.width(256.dp), onLeaveSearch = leaveSearch)
                                val mod = Modifier.weight(1f)
                                when {
                                    screen == "settings" -> SettingsScreen(onBack = { screen = "sheet" }, modifier = mod, showHeader = false,
                                        onOpenDefinitions = { open(app.library.definitions().id) })
                                    session == null -> EmptyState(mod) { newSheet() }
                                    else -> EditorPane(session, true, mod, onMessage = ::message, focus = editorFocus)
                                }
                            }
                        }
                    } else {
                        BackHandler(enabled = !showList && screen == "sheet" && session != null) { showList = true }
                        BackHandler(enabled = screen == "settings") { screen = "sheet" }
                        when {
                            screen == "settings" -> Column { CaptionSpacer(cap, MaterialTheme.colorScheme.surface); SettingsScreen(onBack = { screen = "sheet" }, onOpenDefinitions = { open(app.library.definitions().id) }) }
                            showList || session == null -> Column {
                                CaptionSpacer(cap, MaterialTheme.colorScheme.surface)
                                PhoneList(app.library, index, search, searchFocus, actions.copy(newWindow = null), ::newSheet, { screen = "settings" }, deleted, ::undoDelete)
                            }
                            else -> Column(Modifier.fillMaxSize()) {
                                CaptionSpacer(cap, MaterialTheme.colorScheme.surface)
                                HeaderRow(MaterialTheme.colorScheme.surface) {
                                    SheetHeader(session, meta, status, onBack = { showList = true }, onToggleSidebar = null, onSettings = { screen = "settings" },
                                        onMessage = ::message, onNew = ::newSheet, onDelete = ::delete, onNewWindow = null, onMini = null)
                                }
                                EditorPane(session, false, Modifier.weight(1f), onMessage = ::message, focus = editorFocus)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun SheetActions.copy(newWindow: ((String) -> Unit)?) = SheetActions(open, newWindow, duplicate, delete)

/** The window's own title bar area (desktop windows), painted [color] so it matches the header below. */
@Composable
fun CaptionSpacer(cap: CaptionInsets, color: androidx.compose.ui.graphics.Color) {
    if (cap.present) Box(Modifier.fillMaxWidth().height(cap.height).background(color))
}

/** The header row: title and actions, just below the system's title bar. */
@Composable
fun HeaderRow(background: androidx.compose.ui.graphics.Color, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(background).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun EmptyState(modifier: Modifier, onNew: () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No sheet open", style = MaterialTheme.typography.titleLarge)
            androidx.compose.material3.TextButton(onClick = onNew, modifier = Modifier.padding(top = 8.dp)) { Text("New sheet") }
        }
    }
}

/** A round icon button for the header, with a tooltip after a short hover. */
@Composable
fun HeaderButton(sym: String, label: String, shortcut: String? = null, onClick: () -> Unit) =
    TipIconButton(sym, label, shortcut, onClick = onClick)

@Composable
private fun MenuItem(label: String, hint: String? = null, submenu: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = when {
            submenu -> ({ SymIcon(Sym.CHEVRON_RIGHT, size = 18.sp, tint = MaterialTheme.colorScheme.onSurfaceVariant) })
            hint != null -> ({ Text(hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) })
            else -> null
        },
        onClick = onClick,
    )
}

/** Title, a short status, and the mini, Share and More buttons. */
@Composable
fun RowScope.SheetHeader(
    session: Session?, meta: SheetMeta?, status: String?, onBack: (() -> Unit)?, onToggleSidebar: (() -> Unit)?, onSettings: () -> Unit,
    onMessage: (String) -> Unit, onNew: () -> Unit, onDelete: (String) -> Unit,
    onNewWindow: ((String) -> Unit)?, onMini: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    when {
        onBack != null -> HeaderButton(Sym.ARROW_BACK, "Sheets", onClick = onBack)
        onToggleSidebar != null -> HeaderButton(Sym.MENU, "Show or hide the sheet list", "Ctrl+B", onToggleSidebar)
    }
    Text(
        meta?.title?.ifBlank { "Untitled" } ?: "Summa", Modifier.padding(start = 8.dp).weight(1f),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(650)), maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
    if (status != null) Text(status, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, maxLines = 1)
    if (session == null) return
    var share by remember { mutableStateOf(false) }
    var export by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val files = rememberFileActions(session, onMessage)
    if (onMini != null) HeaderButton(Sym.PICTURE_IN_PICTURE_ALT, "Mini calculator", "Ctrl+Shift+M", onMini)
    Box {
        HeaderButton(Sym.IOS_SHARE, "Share") { share = true }
        DropdownMenu(share, onDismissRequest = { share = false }, shape = RoundedCornerShape(14.dp)) {
            MenuItem("Copy with answers") { share = false; copyToClipboard(context, withAnswers(session)); onMessage("Copied") }
            MenuItem("Share as text…") { share = false; shareSheet(context, session) }
            MenuItem("Share as PDF…") { share = false; Export.sharePdf(context, SheetSnapshot.of(session)) }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuItem("Export", submenu = true) { share = false; export = true }
            MenuItem("Print…", "Ctrl+P") { share = false; Export.print(context, SheetSnapshot.of(session)) }
        }
        DropdownMenu(export, onDismissRequest = { export = false }, shape = RoundedCornerShape(14.dp)) {
            MenuItem("PDF…") { export = false; files.exportPdf() }
            MenuItem("Web page (HTML)…") { export = false; files.exportHtml() }
            MenuItem("Spreadsheet (CSV)…") { export = false; files.exportCsv() }
            MenuItem("Markdown…") { export = false; files.exportMarkdown() }
            MenuItem("Plain text…") { export = false; files.exportText() }
        }
    }
    Box {
        HeaderButton(Sym.MORE_VERT, "More") { more = true }
        DropdownMenu(more, onDismissRequest = { more = false }, shape = RoundedCornerShape(14.dp)) {
            MenuItem("New sheet", "Ctrl+N") { more = false; onNew() }
            if (onNewWindow != null) MenuItem("New window", "Ctrl+Shift+N") { more = false; onNewWindow(session.id) }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuItem("Delete sheet") { more = false; onDelete(session.id) }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuItem("Settings", "Ctrl+,") { more = false; onSettings() }
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

/** The sheet, and nothing over it. */
@Composable
fun EditorPane(session: Session, wide: Boolean, modifier: Modifier, onMessage: (String) -> Unit, mini: Boolean = false, focus: FocusRequester? = null) {
    val app = SummaApp.instance
    val settings by app.prefs.state.collectAsState()
    val scroll = rememberScrollState()
    val compact = !wide || mini
    Box(modifier.fillMaxSize().imePadding()) {
        SheetEditor(
            session, settings, scroll = scroll, compact = compact,
            contentPadding = PaddingValues(start = if (compact) 18.dp else 40.dp, end = if (compact) 14.dp else 36.dp, top = if (compact) 8.dp else 14.dp, bottom = 80.dp),
            onMessage = onMessage, focusRequester = focus,
        )
    }
}

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
