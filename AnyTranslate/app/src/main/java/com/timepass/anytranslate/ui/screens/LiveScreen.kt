@file:OptIn(ExperimentalMaterial3Api::class)

package com.timepass.anytranslate.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.timepass.anytranslate.R
import com.timepass.anytranslate.data.Languages
import com.timepass.anytranslate.ui.components.LanguageBar
import com.timepass.anytranslate.ui.components.LanguageButton
import com.timepass.anytranslate.ui.components.LanguagePickerDialog
import com.timepass.anytranslate.ui.components.MicButton
import com.timepass.anytranslate.ui.components.toast

@Composable
fun LiveScreen(vm: LiveViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val langA by vm.langA.collectAsStateWithLifecycle()
    val langB by vm.langB.collectAsStateWithLifecycle()
    val autoSpeak by vm.autoSpeak.collectAsStateWithLifecycle()
    val faceToFace by vm.faceToFace.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<Side?>(null) }

    // Ask for the microphone the first time, then run whatever the user tapped.
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            afterPermission?.invoke()
        } else {
            Toast.makeText(context, "The live translator needs the microphone.", Toast.LENGTH_LONG).show()
        }
        afterPermission = null
    }
    val withMic: (() -> Unit) -> Unit = { action ->
        when {
            !vm.speechAvailable -> context.toast("Speech recognition isn't available. Install or enable the Google app.")
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> action()
            else -> {
                afterPermission = action
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.stopAll() }
    DisposableEffect(Unit) { onDispose { vm.stopAll() } }

    // Keep the screen awake while captioning.
    val view = LocalView.current
    DisposableEffect(state.subtitlesOn) {
        view.keepScreenOn = state.subtitlesOn
        onDispose { view.keepScreenOn = false }
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.mode.ordinal) {
            Tab(
                selected = state.mode == LiveMode.CONVERSATION,
                onClick = { vm.setMode(LiveMode.CONVERSATION) },
                text = { Text("Conversation") },
                icon = { Icon(painterResource(R.drawable.ic_forum), contentDescription = null) },
            )
            Tab(
                selected = state.mode == LiveMode.SUBTITLES,
                onClick = { vm.setMode(LiveMode.SUBTITLES) },
                text = { Text("Live subtitles") },
                icon = { Icon(painterResource(R.drawable.ic_subtitles), contentDescription = null) },
            )
        }

        state.error?.let { error ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = vm::dismissError) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                }
            }
        }

        when (state.mode) {
            LiveMode.CONVERSATION -> ConversationPane(
                state = state,
                langA = langA,
                langB = langB,
                faceToFace = faceToFace,
                autoSpeak = autoSpeak,
                onMic = { side -> withMic { vm.toggleSide(side) } },
                onPickLanguage = { picking = it },
                onSwap = vm::swapLanguages,
                onFaceToFace = vm::setFaceToFace,
                onAutoSpeak = vm::setAutoSpeak,
                onSpeak = { if (!vm.speak(it)) context.toast("No ${Languages.displayName(it.to)} voice installed.") },
                onClear = vm::clear,
            )
            LiveMode.SUBTITLES -> SubtitlesPane(
                state = state,
                langA = langA,
                langB = langB,
                onToggle = { withMic { vm.toggleSubtitles() } },
                onPickLanguage = { picking = it },
                onSwap = vm::swapLanguages,
            )
        }
    }

    picking?.let { side ->
        LanguagePickerDialog(
            title = if (state.mode == LiveMode.SUBTITLES) {
                if (side == Side.A) "Language being spoken" else "Show subtitles in"
            } else {
                if (side == Side.A) "Your language" else "Their language"
            },
            selected = if (side == Side.A) langA else langB,
            allowAuto = false,
            onSelect = { if (side == Side.A) vm.setLangA(it) else vm.setLangB(it) },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun ConversationPane(
    state: LiveState,
    langA: String,
    langB: String,
    faceToFace: Boolean,
    autoSpeak: Boolean,
    onMic: (Side) -> Unit,
    onPickLanguage: (Side) -> Unit,
    onSwap: () -> Unit,
    onFaceToFace: (Boolean) -> Unit,
    onAutoSpeak: (Boolean) -> Unit,
    onSpeak: (Utterance) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = faceToFace, onClick = { onFaceToFace(!faceToFace) }, label = { Text("Face-to-face") })
            FilterChip(selected = autoSpeak, onClick = { onAutoSpeak(!autoSpeak) }, label = { Text("Speak aloud") })
            Spacer(Modifier.weight(1f))
            if (state.messages.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
        }

        if (faceToFace) {
            // The top half is upside down so the person across the table can read it.
            PersonPanel(
                side = Side.B,
                language = langB,
                state = state,
                onMic = onMic,
                onPickLanguage = onPickLanguage,
                modifier = Modifier.weight(1f).graphicsLayer { rotationZ = 180f },
            )
            HorizontalDivider()
            PersonPanel(
                side = Side.A,
                language = langA,
                state = state,
                onMic = onMic,
                onPickLanguage = onPickLanguage,
                modifier = Modifier.weight(1f),
            )
        } else {
            MessageList(state, onSpeak, Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicColumn(Side.A, langA, state, onMic, onPickLanguage, Modifier.weight(1f))
                IconButton(onClick = onSwap) {
                    Icon(painterResource(R.drawable.ic_swap), contentDescription = "Swap languages")
                }
                MicColumn(Side.B, langB, state, onMic, onPickLanguage, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MicColumn(
    side: Side,
    language: String,
    state: LiveState,
    onMic: (Side) -> Unit,
    onPickLanguage: (Side) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val listening = state.listening == side
        MicButton(
            listening = listening,
            level = if (listening) state.level else 0f,
            onClick = { onMic(side) },
            contentDescription = "Speak ${Languages.displayName(language)}",
        )
        LanguageButton(language, onClick = { onPickLanguage(side) })
    }
}

@Composable
private fun MessageList(state: LiveState, onSpeak: (Utterance) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val itemCount = state.messages.size + if (state.listening != null) 1 else 0
    LaunchedEffect(itemCount, state.partial) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    if (itemCount == 0) {
        Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Tap the mic under your language and speak. Then let the other person tap theirs.\n\n" +
                    "Turn on Face-to-face to put the phone on a table between you.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.messages, key = { it.id }) { message ->
            MessageBubble(
                side = message.side,
                original = message.original,
                translated = message.translated ?: "…",
                onSpeak = { onSpeak(message) },
            )
        }
        state.listening?.let { side ->
            item(key = "partial") {
                MessageBubble(
                    side = side,
                    original = state.partial.ifEmpty { "Listening…" },
                    translated = state.partialTranslation,
                    onSpeak = null,
                    live = true,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(side: Side, original: String, translated: String, onSpeak: (() -> Unit)?, live: Boolean = false) {
    val mine = side == Side.A
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterStart else Alignment.CenterEnd) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (mine) 4.dp else 20.dp,
                bottomEnd = if (mine) 20.dp else 4.dp,
            ),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = if (onSpeak != null) 0.dp else 14.dp)) {
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        original,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = if (live) FontStyle.Italic else FontStyle.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (translated.isNotEmpty()) {
                        Text(
                            translated,
                            style = MaterialTheme.typography.titleMedium,
                            fontStyle = if (live) FontStyle.Italic else FontStyle.Normal,
                        )
                    }
                }
                if (onSpeak != null) {
                    IconButton(onClick = onSpeak) {
                        Icon(painterResource(R.drawable.ic_volume), contentDescription = "Listen again")
                    }
                }
            }
        }
    }
}

/** One half of the face-to-face view: what this person needs to read, plus their mic. */
@Composable
private fun PersonPanel(
    side: Side,
    language: String,
    state: LiveState,
    onMic: (Side) -> Unit,
    onPickLanguage: (Side) -> Unit,
    modifier: Modifier = Modifier,
) {
    val other = if (side == Side.A) Side.B else Side.A
    val last = state.messages.lastOrNull()
    val (big, small) = when {
        state.listening == side -> state.partial.ifEmpty { "Listening…" } to ""
        state.listening == other -> state.partialTranslation.ifEmpty { "…" } to ""
        last == null -> "Tap the mic and speak ${Languages.displayName(language)}" to ""
        last.side == other -> (last.translated ?: "…") to last.original
        else -> last.original to (last.translated ?: "")
    }
    val tint = if (side == Side.A) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer

    Column(
        modifier.fillMaxWidth().background(tint.copy(alpha = 0.35f)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                big,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (small.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    small,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LanguageButton(language, onClick = { onPickLanguage(side) })
            MicButton(
                listening = state.listening == side,
                level = if (state.listening == side) state.level else 0f,
                onClick = { onMic(side) },
                contentDescription = "Speak ${Languages.displayName(language)}",
            )
        }
    }
}

@Composable
private fun SubtitlesPane(
    state: LiveState,
    langA: String,
    langB: String,
    onToggle: () -> Unit,
    onPickLanguage: (Side) -> Unit,
    onSwap: () -> Unit,
) {
    val listState = rememberLazyListState()
    val lines = state.messages
    LaunchedEffect(lines.size, state.partial) {
        val count = lines.size + 1
        listState.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        LanguageBar(
            source = langA,
            target = langB,
            onSourceClick = { onPickLanguage(Side.A) },
            onTargetClick = { onPickLanguage(Side.B) },
            onSwap = onSwap,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(lines, key = { it.id }) { line ->
                Column {
                    Text(line.translated ?: "…", style = MaterialTheme.typography.headlineSmall)
                    Text(line.original, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "live") {
                Column(Modifier.padding(bottom = 8.dp)) {
                    when {
                        state.partial.isNotEmpty() -> {
                            Text(
                                state.partialTranslation.ifEmpty { "…" },
                                style = MaterialTheme.typography.headlineSmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                state.partial,
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.subtitlesOn -> Text(
                            "Listening for ${Languages.displayName(langA)}…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        lines.isEmpty() -> Text(
                            "Live subtitles listen continuously and show the translation as people speak — " +
                                "great for lectures, announcements, TV or someone talking to a group.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            MicButton(
                listening = state.subtitlesOn,
                level = state.level,
                onClick = onToggle,
                contentDescription = if (state.subtitlesOn) "Stop subtitles" else "Start subtitles",
            )
            Spacer(Modifier.width(16.dp))
            Text(
                if (state.subtitlesOn) "Tap to stop" else "Tap to start",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
