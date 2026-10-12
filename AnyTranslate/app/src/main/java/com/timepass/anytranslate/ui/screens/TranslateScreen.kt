package com.timepass.anytranslate.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.timepass.anytranslate.R
import com.timepass.anytranslate.data.Languages
import com.timepass.anytranslate.ui.components.LanguageBar
import com.timepass.anytranslate.ui.components.LanguagePickerDialog
import com.timepass.anytranslate.ui.components.ScreenTitle
import com.timepass.anytranslate.ui.components.copyToClipboard
import com.timepass.anytranslate.ui.components.readClipboard
import com.timepass.anytranslate.ui.components.rememberSpeaker
import com.timepass.anytranslate.ui.components.say
import com.timepass.anytranslate.ui.components.shareText
import com.timepass.anytranslate.ui.components.toast

private enum class Picking { SOURCE, TARGET }

@Composable
fun TranslateScreen(sharedText: String?, onSharedTextConsumed: () -> Unit, vm: TranslateViewModel = viewModel()) {
    val context = LocalContext.current
    val source by vm.source.collectAsStateWithLifecycle()
    val target by vm.target.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val downloading by vm.downloading.collectAsStateWithLifecycle()
    val speaker = rememberSpeaker()
    var picking by remember { mutableStateOf<Picking?>(null) }

    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            vm.updateInput(sharedText)
            onSharedTextConsumed()
        }
    }

    val voiceInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) vm.updateInput(spoken)
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Translate", "Type, paste or speak. Works offline once a language is downloaded.")

        LanguageBar(
            source = source,
            target = target,
            onSourceClick = { picking = Picking.SOURCE },
            onTargetClick = { picking = Picking.TARGET },
            onSwap = vm::swap,
            swapEnabled = source != Languages.AUTO || result.detected != null,
        )

        OutlinedTextField(
            value = vm.input,
            onValueChange = vm::updateInput,
            placeholder = { Text("Enter text") },
            minLines = 4,
            textStyle = MaterialTheme.typography.bodyLarge,
            trailingIcon = if (vm.input.isNotEmpty()) {
                {
                    IconButton(onClick = { vm.updateInput("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val clip = context.readClipboard()
                if (clip.isNullOrBlank()) context.toast("Clipboard is empty") else vm.updateInput(clip)
            }) {
                Icon(painterResource(R.drawable.ic_paste), contentDescription = "Paste")
            }
            IconButton(onClick = {
                val speechLanguage = if (source == Languages.AUTO) Languages.deviceLanguage() else source
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Languages.speechTag(speechLanguage))
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in ${Languages.displayName(speechLanguage)}")
                try {
                    voiceInput.launch(intent)
                } catch (_: ActivityNotFoundException) {
                    context.toast("No speech recognizer found. Install the Google app.")
                }
            }) {
                Icon(painterResource(R.drawable.ic_mic), contentDescription = "Speak")
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${vm.input.length} chars",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (downloading.isNotEmpty()) {
            Text(
                "Downloading ${downloading.joinToString { Languages.displayName(it) }} (about 30 MB, one time)…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val label = when {
                    source == Languages.AUTO && result.detected != null ->
                        "${Languages.displayName(result.detected!!)} (detected) → ${Languages.displayName(target)}"
                    else -> Languages.displayName(target)
                }
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

                when {
                    result.loading && result.output.isEmpty() -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    result.error != null -> Text(result.error!!, color = MaterialTheme.colorScheme.error)
                    result.output.isEmpty() -> Text(
                        "Translation appears here",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> SelectionContainer(Modifier.heightIn(min = 48.dp)) {
                        Text(result.output, style = MaterialTheme.typography.titleLarge)
                    }
                }

                if (result.output.isNotEmpty()) {
                    Row {
                        IconButton(onClick = { context.copyToClipboard(result.output) }) {
                            Icon(painterResource(R.drawable.ic_copy), contentDescription = "Copy")
                        }
                        IconButton(onClick = { speaker.say(context, result.output, target) }) {
                            Icon(painterResource(R.drawable.ic_volume), contentDescription = "Listen")
                        }
                        IconButton(onClick = { context.shareText(result.output) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                }
            }
        }
    }

    when (picking) {
        Picking.SOURCE -> LanguagePickerDialog(
            title = "Translate from",
            selected = source,
            allowAuto = true,
            onSelect = vm::setSource,
            onDismiss = { picking = null },
        )
        Picking.TARGET -> LanguagePickerDialog(
            title = "Translate to",
            selected = target,
            allowAuto = false,
            onSelect = vm::setTarget,
            onDismiss = { picking = null },
        )
        null -> Unit
    }
}
