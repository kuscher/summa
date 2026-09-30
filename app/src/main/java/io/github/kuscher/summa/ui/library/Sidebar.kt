package io.github.kuscher.summa.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.summa.data.Library
import io.github.kuscher.summa.data.LibraryIndex
import io.github.kuscher.summa.data.SheetMeta
import io.github.kuscher.summa.ui.Sym
import io.github.kuscher.summa.ui.SymIcon
import io.github.kuscher.summa.ui.onSecondaryClick
import io.github.kuscher.summa.ui.theme.SummaFonts

/** An icon that fits the sheet's title: a trip gets a plane, a budget a piggy bank. */
fun iconFor(title: String): String {
    val t = title.lowercase()
    fun has(vararg w: String) = w.any { it in t }
    return when {
        has("trip", "travel", "flight", "holiday", "vacation", "weekend", "urlaub", "reise") -> Sym.FLIGHT
        has("budget", "saving", "money", "salary", "finance", "tax") -> Sym.SAVINGS
        has("invoice", "receipt", "bill", "expense") -> Sym.RECEIPT_LONG
        has("recipe", "cook", "baking", "dinner", "food", "sourdough", "coffee") -> Sym.RESTAURANT
        has("home", "house", "rent", "kitchen", "flat", "apartment", "garden") -> Sym.HOME
        has("work", "project", "client", "meeting") -> Sym.WORK
        has("shop", "grocer", "buy") -> Sym.SHOPPING_CART
        has("build", "renovat", "diy", "wood", "floor") -> Sym.CONSTRUCTION
        has("science", "physics", "chem") -> Sym.SCIENCE
        has("school", "study", "homework") -> Sym.SCHOOL
        has("run", "gym", "fitness", "training", "pace") -> Sym.FITNESS_CENTER
        has("welcome", "tour", "hello") -> Sym.WAVING_HAND
        has("time", "zone", "schedule") -> Sym.SCHEDULE
        else -> Sym.DESCRIPTION
    }
}

sealed interface SideSel {
    data object Sheets : SideSel
    data object Trash : SideSel
    data class Folder(val id: String) : SideSel
}

@Composable
fun SearchField(state: TextFieldState, modifier: Modifier = Modifier, focus: FocusRequester? = null, hint: String = "Search sheets") {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(scheme.surfaceContainerHigh).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymIcon(Sym.SEARCH, size = 20.sp, tint = scheme.onSurfaceVariant)
        Box(Modifier.weight(1f).padding(start = 10.dp)) {
            if (state.text.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            BasicTextField(
                state = state, lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine, cursorBrush = SolidColor(scheme.primary),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                modifier = Modifier.fillMaxWidth().let { if (focus != null) it.focusRequester(focus) else it }.semantics { contentDescription = hint },
            )
        }
        if (state.text.isEmpty()) Text("Ctrl K", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

/** The desktop sidebar: search, New sheet, pinned and recent sheets, folders and trash. */
@Composable
fun Sidebar(
    library: Library,
    index: LibraryIndex,
    current: String?,
    search: TextFieldState,
    searchFocus: FocusRequester,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onMini: (() -> Unit)?,
    onNewWindow: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    var sel by remember { mutableStateOf<SideSel>(SideSel.Sheets) }
    var newFolder by remember { mutableStateOf(false) }
    val q = search.text.toString()
    val live = index.sheets.filter { it.trashedAt == null }
    Column(modifier.fillMaxHeight().background(if (compact) scheme.surface else scheme.surfaceContainerLow).padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(10.dp))
        SearchField(search, focus = searchFocus)
        Spacer(Modifier.height(10.dp))
        if (!compact) {
            ExtendedFloatingActionButton(
                onClick = onNew, shape = RoundedCornerShape(18.dp),
                containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer,
                icon = { SymIcon(Sym.ADD, size = 22.sp) }, text = { Text("New sheet", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)) },
            )
            Spacer(Modifier.height(6.dp))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
            if (q.isNotBlank()) {
                val found = library.search(q)
                item { SectionLabel(if (found.isEmpty()) "No sheets match" else "Results") }
                items(found, key = { "s" + it.id }) { m -> SheetRow(m, m.id == current, library, index, onOpen, onNewWindow) }
                return@LazyColumn
            }
            when (val s = sel) {
                SideSel.Trash -> {
                    val trashed = index.sheets.filter { it.trashedAt != null }.sortedByDescending { it.trashedAt }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("Trash", Modifier.weight(1f))
                            if (trashed.isNotEmpty()) TextButton(onClick = { library.emptyTrash() }) { Text("Empty") }
                        }
                    }
                    if (trashed.isEmpty()) item { Text("Nothing here. Deleted sheets wait here until you empty the trash.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
                    items(trashed, key = { "t" + it.id }) { m -> SheetRow(m, false, library, index, onOpen = { library.restore(it); onOpen(it) }, onNewWindow = null, trashed = true) }
                    item { NavRow(Sym.ARROW_BACK, "All sheets", false) { sel = SideSel.Sheets } }
                }
                else -> {
                    val pinned = live.filter { it.pinned }.sortedByDescending { it.updated }
                    if (pinned.isNotEmpty()) {
                        item { SectionLabel("Pinned") }
                        items(pinned, key = { "p" + it.id }) { m -> SheetRow(m, m.id == current, library, index, onOpen, onNewWindow) }
                    }
                    val folderSel = (s as? SideSel.Folder)?.id
                    val recent = live.filter { !it.pinned && it.folder == folderSel }.sortedByDescending { it.updated }
                    item { SectionLabel(index.folders.firstOrNull { it.id == folderSel }?.name ?: "Recent") }
                    if (recent.isEmpty()) item { Text("No sheets yet", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
                    items(recent, key = { "r" + it.id }) { m -> SheetRow(m, m.id == current, library, index, onOpen, onNewWindow) }
                    item { SectionLabel("Folders") }
                    if (folderSel != null) item { NavRow(Sym.ARROW_BACK, "All sheets", false) { sel = SideSel.Sheets } }
                    items(index.folders.sortedBy { it.name.lowercase() }, key = { "f" + it.id }) { f ->
                        val count = live.count { it.folder == f.id }
                        FolderRow(f.id, f.name, count, folderSel == f.id, library) { sel = if (folderSel == f.id) SideSel.Sheets else SideSel.Folder(f.id) }
                    }
                    item { NavRow(Sym.CREATE_NEW_FOLDER, "New folder", false) { newFolder = true } }
                    val trashCount = index.sheets.count { it.trashedAt != null }
                    item { NavRow(Sym.DELETE, "Trash", false, trailing = if (trashCount > 0) "$trashCount" else null) { sel = SideSel.Trash } }
                }
            }
        }
        if (!compact) {
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onMini != null) {
                    Row(
                        Modifier.clip(CircleShape).background(scheme.surfaceContainerHigh).clickable(onClick = onMini).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SymIcon(Sym.PICTURE_IN_PICTURE_ALT, size = 19.sp)
                        Text("Mini", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onSettings).semantics { contentDescription = "Settings" }, contentAlignment = Alignment.Center) {
                    SymIcon(Sym.SETTINGS, tint = scheme.onSurfaceVariant)
                }
            }
        }
    }
    if (newFolder) NameDialog("New folder", "") { name -> newFolder = false; if (!name.isNullOrBlank()) library.createFolder(name.trim()) }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp),
        style = TextStyle(fontFamily = SummaFonts.sans, fontSize = 11.5.sp, fontWeight = FontWeight(680), letterSpacing = 0.9.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NavRow(sym: String, label: String, selected: Boolean, trailing: String? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(42.dp).clip(CircleShape).background(if (selected) scheme.secondaryContainer else scheme.surface.copy(alpha = 0f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymIcon(sym, size = 20.sp, tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun FolderRow(id: String, name: String, count: Int, open: Boolean, library: Library, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.onSecondaryClick { menu = true }) {
            NavRow(if (open) Sym.FOLDER_OPEN else Sym.FOLDER, name, open, trailing = "$count", onClick = onClick)
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(18.dp)) {
            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { SymIcon(Sym.EDIT, size = 20.sp) }, onClick = { menu = false; rename = true })
            DropdownMenuItem(text = { Text("Delete folder") }, leadingIcon = { SymIcon(Sym.DELETE, size = 20.sp) }, onClick = { menu = false; library.deleteFolder(id) })
        }
    }
    if (rename) NameDialog("Rename folder", name) { n -> rename = false; if (!n.isNullOrBlank()) library.renameFolder(id, n.trim()) }
}

@Composable
fun SheetRow(
    m: SheetMeta, selected: Boolean, library: Library, index: LibraryIndex, onOpen: (String) -> Unit,
    onNewWindow: ((String) -> Unit)?, trashed: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var moveMenu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().height(44.dp).clip(CircleShape)
                .background(if (selected) scheme.secondaryContainer else scheme.surface.copy(alpha = 0f))
                .onSecondaryClick { menu = true }
                .clickable { onOpen(m.id) }
                .padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fg = if (selected) scheme.onSecondaryContainer else scheme.onSurface
            SymIcon(iconFor(m.title), size = 20.sp, filled = selected, tint = if (selected) fg else scheme.onSurfaceVariant)
            Text(
                m.title.ifBlank { "Untitled" }, Modifier.weight(1f).padding(start = 12.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight(650) else FontWeight(420)),
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg,
            )
            m.total?.let {
                Text(it, Modifier.padding(start = 6.dp), maxLines = 1,
                    style = TextStyle(fontFamily = SummaFonts.round, fontSize = 12.5.sp, fontWeight = FontWeight(600), fontFeatureSettings = "tnum"),
                    color = if (selected) fg else scheme.onSurfaceVariant)
            }
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(18.dp)) {
            if (trashed) {
                DropdownMenuItem(text = { Text("Restore") }, leadingIcon = { SymIcon(Sym.RESTORE_FROM_TRASH, size = 20.sp) }, onClick = { menu = false; library.restore(m.id) })
                DropdownMenuItem(text = { Text("Delete forever") }, leadingIcon = { SymIcon(Sym.DELETE_FOREVER, size = 20.sp) }, onClick = { menu = false; library.deleteForever(m.id) })
            } else {
                DropdownMenuItem(text = { Text(if (m.pinned) "Unpin" else "Pin") }, leadingIcon = { SymIcon(if (m.pinned) Sym.KEEP_OFF else Sym.KEEP, size = 20.sp) },
                    onClick = { menu = false; library.pin(m.id, !m.pinned) })
                if (onNewWindow != null) DropdownMenuItem(text = { Text("Open in new window") }, leadingIcon = { SymIcon(Sym.OPEN_IN_NEW, size = 20.sp) },
                    onClick = { menu = false; onNewWindow(m.id) })
                DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { SymIcon(Sym.CONTENT_COPY, size = 20.sp) },
                    onClick = { menu = false; onOpen(library.duplicate(m.id).id) })
                DropdownMenuItem(text = { Text("Move to folder") }, leadingIcon = { SymIcon(Sym.FOLDER, size = 20.sp) },
                    onClick = { menu = false; moveMenu = true })
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(text = { Text("Move to trash") }, leadingIcon = { SymIcon(Sym.DELETE, size = 20.sp) }, onClick = { menu = false; library.trash(m.id) })
            }
        }
        DropdownMenu(moveMenu, onDismissRequest = { moveMenu = false }, shape = RoundedCornerShape(18.dp)) {
            DropdownMenuItem(text = { Text("No folder") }, onClick = { moveMenu = false; library.move(m.id, null) })
            for (f in index.folders) DropdownMenuItem(text = { Text(f.name) }, leadingIcon = { SymIcon(Sym.FOLDER, size = 20.sp) },
                onClick = { moveMenu = false; library.move(m.id, f.id) })
            if (index.folders.isEmpty()) Text("Make a folder first (Folders › New folder).", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDone: (String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, shape = RoundedCornerShape(14.dp)) },
        confirmButton = { TextButton(onClick = { onDone(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

/** Phones: the library as a full screen list. */
@Composable
fun LibraryScreen(
    library: Library, index: LibraryIndex, current: String?, search: TextFieldState, searchFocus: FocusRequester,
    onOpen: (String) -> Unit, onNew: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(scheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Summa", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight(800)))
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onSettings).semantics { contentDescription = "Settings" }, contentAlignment = Alignment.Center) {
                    SymIcon(Sym.SETTINGS)
                }
            }
            Sidebar(library, index, current, search, searchFocus, onOpen, onNew, onSettings, null, null, Modifier.weight(1f), compact = true)
        }
        ExtendedFloatingActionButton(
            onClick = onNew, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp), shape = RoundedCornerShape(20.dp),
            containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer,
            icon = { SymIcon(Sym.ADD) }, text = { Text("New sheet") },
        )
    }
}

