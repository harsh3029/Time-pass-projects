package com.timepass.anytranslate.quick

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timepass.anytranslate.MainActivity
import com.timepass.anytranslate.R
import com.timepass.anytranslate.data.AppPrefs
import com.timepass.anytranslate.data.Languages
import com.timepass.anytranslate.translate.TranslationEngine
import com.timepass.anytranslate.translate.TranslationResult
import com.timepass.anytranslate.ui.components.LanguagePickerDialog
import com.timepass.anytranslate.ui.components.copyToClipboard
import com.timepass.anytranslate.ui.components.rememberSpeaker
import com.timepass.anytranslate.ui.components.say
import com.timepass.anytranslate.ui.components.shareText
import com.timepass.anytranslate.ui.theme.AnyTranslateTheme
import kotlinx.coroutines.CancellationException

/**
 * Pops up over any app when the user picks "AnyTranslate" in a text-selection menu or the share sheet.
 * For editable text it can replace the selection with the translation.
 */
class QuickTranslateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            else -> null
        }?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            finish()
            return
        }
        val canReplace = intent.action == Intent.ACTION_PROCESS_TEXT &&
            !intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)

        enableEdgeToEdge()
        setContent {
            AnyTranslateTheme {
                QuickTranslateSheet(
                    text = text,
                    canReplace = canReplace,
                    onReplace = { translated ->
                        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, translated))
                        finish()
                    },
                    onOpenApp = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(MainActivity.EXTRA_TEXT, text)
                        )
                        finish()
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }
}

@Composable
private fun QuickTranslateSheet(
    text: String,
    canReplace: Boolean,
    onReplace: (String) -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs.get(context) }
    val engine = remember { TranslationEngine.get() }
    val target by prefs.quickTarget.flow.collectAsStateWithLifecycle()
    val downloading by engine.downloading.collectAsStateWithLifecycle()
    val speaker = rememberSpeaker()
    var result by remember { mutableStateOf<TranslationResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }

    LaunchedEffect(target) {
        result = null
        error = null
        try {
            result = engine.translate(text, Languages.AUTO, target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Translation failed"
        }
    }

    val noIndication = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = noIndication, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 3.dp,
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                // Swallow taps so they don't fall through to the dismiss scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_translate), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("AnyTranslate", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        result?.let { Languages.displayName(it.sourceLanguage) } ?: "Detecting…",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text("  →  ", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { picking = true }) {
                        Text(Languages.displayName(target))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }

                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                HorizontalDivider()

                val current = result
                val currentError = error
                Box(Modifier.heightIn(min = 48.dp, max = 300.dp)) {
                    when {
                        currentError != null -> Text(currentError, color = MaterialTheme.colorScheme.error)
                        current == null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (downloading.isNotEmpty()) {
                                Text(
                                    "Downloading the ${downloading.joinToString { Languages.displayName(it) }} language pack (one time)…",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        current.sourceLanguage == target -> Text(
                            "This is already in ${Languages.displayName(target)}. Pick another language above.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        else -> SelectionContainer {
                            Text(
                                current.text,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val translated = current?.takeIf { it.sourceLanguage != target }?.text
                    IconButton(onClick = { translated?.let(context::copyToClipboard) }, enabled = translated != null) {
                        Icon(painterResource(R.drawable.ic_copy), contentDescription = "Copy")
                    }
                    IconButton(onClick = { translated?.let { speaker.say(context, it, target) } }, enabled = translated != null) {
                        Icon(painterResource(R.drawable.ic_volume), contentDescription = "Listen")
                    }
                    IconButton(onClick = { translated?.let(context::shareText) }, enabled = translated != null) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    Spacer(Modifier.weight(1f))
                    if (canReplace) {
                        Button(onClick = { translated?.let(onReplace) }, enabled = translated != null) { Text("Replace") }
                    } else {
                        OutlinedButton(onClick = onOpenApp) { Text("Open app") }
                    }
                }
            }
        }
    }

    if (picking) {
        LanguagePickerDialog(
            title = "Translate to",
            selected = target,
            allowAuto = false,
            onSelect = { prefs.quickTarget.value = it },
            onDismiss = { picking = false },
        )
    }
}
