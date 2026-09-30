package io.github.kuscher.summa.ui

import android.app.Activity
import android.os.Build
import android.view.WindowInsetsController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Desktop windows (Googlebook OS, Android 15+): Summa draws its own header into the window's
 * caption bar. The system still draws its app handle (left) and window buttons (right) on top, so
 * the header keeps clear of their rectangles; empty header space still drags the window, and
 * buttons opt out with Modifier.systemGestureExclusion().
 */
object Caption {
    fun enable(activity: Activity, lightBackground: Boolean) {
        if (Build.VERSION.SDK_INT < 35) return
        val c = activity.window.insetsController ?: return
        c.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
            WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
        )
        c.setSystemBarsAppearance(
            if (lightBackground) WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS,
        )
    }
}

/** The caption bar's height and the space the system's own controls take at each end. */
@Immutable
data class CaptionInsets(val height: Dp, val start: Dp, val end: Dp) {
    val present: Boolean get() = height > 0.dp
}

@Composable
fun rememberCaptionInsets(): CaptionInsets {
    val density = LocalDensity.current
    val top = WindowInsets.captionBar.getTop(density) // read so we recompose when it changes
    if (top <= 0) return CaptionInsets(0.dp, 0.dp, 0.dp)
    val view = LocalView.current
    val width = view.width
    var start = 0
    var end = 0
    if (Build.VERSION.SDK_INT >= 35) {
        val rects = view.rootWindowInsets?.getBoundingRects(android.view.WindowInsets.Type.captionBar()).orEmpty()
        for (r in rects) {
            if (r.left < width / 2) start = maxOf(start, r.right) else end = maxOf(end, width - r.left)
        }
    }
    return with(density) { CaptionInsets(top.toDp(), start.toDp(), end.toDp()) }
}
