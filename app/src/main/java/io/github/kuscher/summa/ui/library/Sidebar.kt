package io.github.kuscher.summa.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Today", "Yesterday", "Sep 26", "Sep 26, 2025". */
fun whenLabel(millis: Long, today: LocalDate = LocalDate.now()): String {
    val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return when {
        d == today -> "Today"
        d == today.minusDays(1) -> "Yesterday"
        d.year == today.year -> d.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
        else -> d.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
    }
}

/** Sheets you can open: newest first, or the ones matching [query]. */
fun visibleSheets(library: Library, index: LibraryIndex, query: String): List<SheetMeta> =
    if (query.isNotBlank()) library.search(query)
    else index.sheets.filter { it.trashedAt == null && !Library.isSpecial(it.id) }.sortedByDescending { it.updated }

/** What a sheet row can do. */
class SheetActions(
    val open: (String) -> Unit,
    val newWindow: ((String) -> Unit)?,
    val duplicate: (String) -> Unit,
    val delete: (String) -> Unit,
)

@Composable
fun SearchField(state: TextFieldState, modifier: Modifier = Modifier, focus: FocusRequester? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainer).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymIcon(Sym.SEARCH, size = 18.sp, tint = scheme.onSurfaceVariant)
        Box(Modifier.weight(1f).padding(start = 8.dp)) {
            if (state.text.isEmpty()) Text("Search", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            BasicTextField(
                state = state, lineLimits = TextFieldLineLimits.SingleLine, cursorBrush = SolidColor(scheme.primary),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                modifier = Modifier.fillMaxWidth().let { if (focus != null) it.focusRequester(focus) else it }.semantics { contentDescription = "Search sheets" },
            )
        }
    }
}

/** An icon button with a tooltip (after a short hover). */
@Composable
private fun IconBtn(sym: String, label: String, size: Int = 36, shortcut: String? = null, onClick: () -> Unit) =
    io.github.kuscher.summa.ui.TipIconButton(sym, label, shortcut, size.dp, onClick = onClick)

/** "Deleted “X” · Undo", at the top of the list for a few seconds after a delete. */
@Composable
fun UndoRow(deleted: SheetMeta, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainer).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Deleted “${deleted.title.ifBlank { "Untitled" }}”", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onUndo) { Text("Undo", fontWeight = FontWeight(650)) }
    }
}

/** The desktop sheet history: search, +, and the sheets, newest first. */
@Composable
fun Sidebar(
    library: Library, index: LibraryIndex, current: String?, search: TextFieldState, searchFocus: FocusRequester,
    actions: SheetActions, onNew: () -> Unit, deleted: SheetMeta?, onUndo: () -> Unit, modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val q = search.text.toString()
    Column(modifier.fillMaxHeight().background(scheme.surfaceContainerLow).padding(horizontal = 10.dp)) {
        Row(Modifier.padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchField(search, Modifier.weight(1f), searchFocus)
            IconBtn(Sym.ADD, "New sheet", shortcut = "Ctrl+N", onClick = onNew)
        }
        val sheets = visibleSheets(library, index, q)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (deleted != null) item(key = "undo") { UndoRow(deleted, onUndo) }
            if (sheets.isEmpty()) item {
                Text(if (q.isBlank()) "No sheets yet" else "No sheets match", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            items(sheets, key = { it.id }) { m -> HistoryRow(m, m.id == current, actions) }
        }
    }
}

@Composable
private fun HistoryRow(m: SheetMeta, selected: Boolean, actions: SheetActions) {
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp))
                .background(if (selected) scheme.surfaceContainerHigh else scheme.surface.copy(alpha = 0f))
                .onSecondaryClick { menu = true }
                .combinedClickable(onClickLabel = "Open", onLongClick = { menu = true }) { actions.open(m.id) }
                .semantics { customActions = listOf(CustomAccessibilityAction("Sheet options") { menu = true; true }) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                m.title.ifBlank { "Untitled" }, Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, fontWeight = FontWeight(if (selected) 620 else 450)),
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.onSurface,
            )
            Text(whenLabel(m.updated), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        SheetMenu(m.id, menu, { menu = false }, actions)
    }
}

@Composable
private fun SheetMenu(id: String, open: Boolean, onDismiss: () -> Unit, actions: SheetActions) {
    DropdownMenu(open, onDismissRequest = onDismiss, shape = RoundedCornerShape(14.dp)) {
        if (actions.newWindow != null) DropdownMenuItem(text = { Text("Open in new window") }, onClick = { onDismiss(); actions.newWindow.invoke(id) })
        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { onDismiss(); actions.duplicate(id) })
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        DropdownMenuItem(text = { Text("Delete") }, onClick = { onDismiss(); actions.delete(id) })
    }
}

/** Phones: the sheets as a full-screen list, with search, + and settings in the top bar. */
@Composable
fun PhoneList(
    library: Library, index: LibraryIndex, search: TextFieldState, searchFocus: FocusRequester,
    actions: SheetActions, onNew: () -> Unit, onSettings: () -> Unit, deleted: SheetMeta?, onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var searching by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(scheme.surface)) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Summa", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight(800)))
            IconBtn(Sym.SEARCH, "Search sheets", 44) { searching = !searching; if (!searching) search.edit { replace(0, length, "") } }
            IconBtn(Sym.ADD, "New sheet", 44, onClick = onNew)
            Box {
                IconBtn(Sym.MORE_VERT, "More", 44) { more = true }
                DropdownMenu(more, onDismissRequest = { more = false }, shape = RoundedCornerShape(14.dp)) {
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { more = false; onSettings() })
                }
            }
        }
        if (searching) SearchField(search, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), searchFocus)
        val sheets = visibleSheets(library, index, search.text.toString())
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            if (deleted != null) item(key = "undo") { UndoRow(deleted, onUndo, Modifier.padding(bottom = 6.dp)) }
            if (sheets.isEmpty()) item {
                Text("No sheets yet. Tap + to start one.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            items(sheets, key = { it.id }) { m ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    Column(
                        Modifier.fillMaxWidth().height(64.dp).onSecondaryClick { menu = true }
                            .combinedClickable(onClickLabel = "Open", onLongClick = { menu = true }) { actions.open(m.id) }
                            .semantics { customActions = listOf(CustomAccessibilityAction("Sheet options") { menu = true; true }) }
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(m.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(560)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(whenLabel(m.updated), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    SheetMenu(m.id, menu, { menu = false }, actions)
                }
                HorizontalDivider(color = scheme.surfaceContainerHighest)
            }
        }
    }
}
