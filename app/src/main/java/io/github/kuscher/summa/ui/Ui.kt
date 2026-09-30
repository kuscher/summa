package io.github.kuscher.summa.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private var symbols: FontFamily? = null
private var symbolsFill: FontFamily? = null

fun symFont(context: Context, filled: Boolean): FontFamily = if (filled) {
    symbolsFill ?: FontFamily(Font("fonts/MaterialSymbolsRounded_Fill.ttf", context.assets)).also { symbolsFill = it }
} else {
    symbols ?: FontFamily(Font("fonts/MaterialSymbolsRounded.ttf", context.assets)).also { symbols = it }
}

/** A Material Symbols Rounded glyph (see [Sym]). */
@Composable
fun SymIcon(sym: String, modifier: Modifier = Modifier, size: TextUnit = 22.sp, filled: Boolean = false, tint: Color = LocalContentColor.current) {
    Text(
        sym, modifier = modifier,
        style = TextStyle(fontFamily = symFont(LocalContext.current, filled), fontSize = size, lineHeight = size, color = tint, textAlign = TextAlign.Center),
    )
}

/** Right-click with a mouse (or a two-finger trackpad click); [onLongPressTouch] gives touch users the same menu. */
fun Modifier.onSecondaryClick(onClick: (Offset) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val event = currentEvent
        if (down.type == PointerType.Mouse && event.buttons.isSecondaryPressed) {
            down.consume()
            onClick(down.position)
        }
    }
}

fun copyToClipboard(context: Context, text: String, label: String = "Summa") {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Keeps the digits and separators of an answer: "$1,234.50" → "1234.50" for "copy number only". */
fun numberOnly(answer: String, decimalSep: Char, groupSep: Char): String {
    val sb = StringBuilder()
    for (c in answer) when {
        c.isDigit() -> sb.append(c)
        c == decimalSep -> sb.append(c)
        c == '-' && sb.isEmpty() -> sb.append(c)
        c == groupSep -> {}
        c == 'e' && sb.isNotEmpty() -> sb.append(c)
    }
    return sb.toString().ifEmpty { answer }
}

/** Summa's app icon (the launcher's two layers through a circle), for places inside the app. */
@Composable
fun SummaMark(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape)
            .semantics { contentDescription = "Summa" },
    ) {
        // The adaptive layers are 108 dp with the middle 72 dp visible: scale 1.5× to fill.
        for (res in listOf(io.github.kuscher.summa.R.drawable.ic_launcher_background, io.github.kuscher.summa.R.drawable.ic_launcher_foreground)) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(res), contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f },
            )
        }
    }
}
