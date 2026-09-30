package io.github.kuscher.summa.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.summa.SummaApp
import io.github.kuscher.summa.engine.SheetEngine
import io.github.kuscher.summa.ui.theme.SummaFonts
import io.github.kuscher.summa.ui.theme.SummaTheme

/**
 * "Calculate" in any app's text-selection menu (ACTION_PROCESS_TEXT): shows the answers for the
 * selected text in a small card. Copy the answer, replace the selection (in editable text), or
 * open the text as a new sheet.
 */
class CalculateActivity : ComponentActivity() {
    companion object { var instance: java.lang.ref.WeakReference<CalculateActivity> = java.lang.ref.WeakReference(null) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = java.lang.ref.WeakReference(this)
        val app = SummaApp.instance
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty().trim()
        val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        val engine = SheetEngine(app.prefs.engineSettings(), app.rates.rates.value).also { it.definitions = app.definitions.value }
        val result = engine.evaluate(text)
        val lines = text.split('\n')
        val answers = result.lines.map { it.answer }
        val last = answers.lastOrNull { it != null }
        setContent {
            val settings by app.prefs.state.collectAsState()
            SummaTheme(settings) {
                val scheme = MaterialTheme.colorScheme
                Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHigh, modifier = Modifier.width(420.dp)) {
                    Column(Modifier.padding(22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SummaMark(22.dp)
                            Text("Summa", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(700)))
                        }
                        Spacer(Modifier.padding(top = 12.dp))
                        if (answers.count { it != null } <= 1) {
                            Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(
                                last ?: "No answer", modifier = Modifier.padding(top = 6.dp),
                                style = TextStyle(fontFamily = SummaFonts.display, fontSize = if (last != null) 34.sp else 22.sp,
                                    fontWeight = FontWeight(820), fontFeatureSettings = "tnum", color = if (last != null) scheme.onSurface else scheme.onSurfaceVariant),
                            )
                        } else {
                            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                for ((i, l) in lines.withIndex()) Row {
                                    Text(l, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    answers.getOrNull(i)?.let { Text(it, Modifier.padding(start = 12.dp), style = TextStyle(fontFamily = SummaFonts.round, fontWeight = FontWeight(650), fontSize = 15.sp, fontFeatureSettings = "tnum")) }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                val sheet = app.library.create(text)
                                startActivity(Intent(this@CalculateActivity, MainActivity::class.java).putExtra(MainActivity.EXTRA_SHEET, sheet.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                finish()
                            }) { Text("Open in Summa") }
                            if (!readOnly && last != null) FilledTonalButton(onClick = {
                                // Replace keeps the text and adds the answer: "12 × €4 = €48.00".
                                val out = lines.mapIndexed { i, l -> answers.getOrNull(i)?.let { "$l = $it" } ?: l }.joinToString("\n")
                                setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, out))
                                finish()
                            }) { Text("Insert answer") }
                            if (last != null) Button(onClick = {
                                copyToClipboard(this@CalculateActivity, if (answers.count { it != null } > 1) answers.filterNotNull().joinToString("\n") else last)
                                finish()
                            }) { Text("Copy") }
                        }
                    }
                }
            }
        }
    }
}
