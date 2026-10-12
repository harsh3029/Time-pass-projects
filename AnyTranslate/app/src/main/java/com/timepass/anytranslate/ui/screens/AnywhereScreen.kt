package com.timepass.anytranslate.ui.screens

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timepass.anytranslate.R
import com.timepass.anytranslate.anywhere.TranslateAccessibilityService
import com.timepass.anytranslate.anywhere.TranslateTileService
import com.timepass.anytranslate.data.AppPrefs
import com.timepass.anytranslate.data.Languages
import com.timepass.anytranslate.ui.components.LanguageBar
import com.timepass.anytranslate.ui.components.LanguagePickerDialog
import com.timepass.anytranslate.ui.components.ScreenTitle
import com.timepass.anytranslate.ui.components.SwitchRow
import com.timepass.anytranslate.ui.components.toast
import androidx.compose.material3.Icon as MaterialIcon

@Composable
fun AnywhereScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPrefs.get(context) }
    val running by TranslateAccessibilityService.running.collectAsStateWithLifecycle()
    val showBubble by prefs.showBubble.flow.collectAsStateWithLifecycle()
    val useOcr by prefs.useOcr.flow.collectAsStateWithLifecycle()
    val source by prefs.screenSource.flow.collectAsStateWithLifecycle()
    val target by prefs.screenTarget.flow.collectAsStateWithLifecycle()
    var showDisclosure by remember { mutableStateOf(false) }
    var pickingSource by remember { mutableStateOf<Boolean?>(null) }
    val ocrSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Translate anywhere", "Use AnyTranslate on top of any app: chats, browsers, social media, games.")

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(R.drawable.ic_layers, "Translate the whole screen")
                Text(
                    "A floating button stays on top of every app. Tap it and all the text on screen is " +
                        "covered with its translation. Tap again (or anywhere outside a box) to go back.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                StatusRow(running)
                if (running) {
                    OutlinedButton(onClick = { openAccessibilitySettings(context) }) { Text("Accessibility settings") }
                } else {
                    Button(onClick = { showDisclosure = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Turn on screen translation")
                    }
                }

                HorizontalDivider()
                Text("Languages", style = MaterialTheme.typography.titleSmall)
                LanguageBar(
                    source = source,
                    target = target,
                    onSourceClick = { pickingSource = true },
                    onTargetClick = { pickingSource = false },
                    onSwap = {
                        prefs.screenSource.value = target
                        prefs.screenTarget.value = source
                    },
                    swapEnabled = source != Languages.AUTO,
                )
                SwitchRow(
                    title = "Floating translate button",
                    subtitle = "Drag it anywhere. Long-press it to open AnyTranslate.",
                    checked = showBubble,
                    onCheckedChange = { prefs.showBubble.value = it },
                )
                SwitchRow(
                    title = "Read text inside images",
                    subtitle = if (ocrSupported) {
                        "Also translates text in photos, memes and games. For Chinese, Japanese, Korean or " +
                            "Hindi images, set “from” to that language."
                    } else {
                        "Needs Android 11 or newer."
                    },
                    checked = useOcr && ocrSupported,
                    onCheckedChange = { prefs.useOcr.value = it },
                    enabled = ocrSupported,
                )

                HorizontalDivider()
                Text("Other ways to trigger it", style = MaterialTheme.typography.titleSmall)
                Text(
                    "• The “Translate screen” Quick Settings tile\n" +
                        "• The accessibility shortcut button / gesture (set it up in Accessibility settings)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(onClick = { requestAddTile(context) }) { Text("Add Quick Settings tile") }
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(R.drawable.ic_translate, "Translate selected text")
                Text(
                    "In any app, select some text and pick “AnyTranslate” from the pop-up menu (tap ⋮ if you " +
                        "don't see it). In a text box you can tap “Replace” to swap your words for the translation, " +
                        "perfect for replying to someone in their language.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Try it: long-press this text", style = MaterialTheme.typography.labelLarge)
                SelectableSample("¡Hola! ¿Cómo estás? Me encanta aprender idiomas nuevos.")
                Text(
                    "You can also use Share → AnyTranslate from any app.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("Allow screen access") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "To translate other apps, AnyTranslate uses Android's Accessibility Service API to read " +
                            "the text that is on screen, and, if “Read text inside images” is on, a screenshot. " +
                            "This only happens when you tap the translate button, tile or shortcut."
                    )
                    Text("Everything is translated on your phone. Nothing you see is stored or sent anywhere.")
                    Text("On the next screen open “AnyTranslate screen translator” (it may be under “Downloaded apps”) and switch it on.")
                    Text(
                        "Switch greyed out? On Android 13+, apps installed outside the Play Store first need: " +
                            "App info → ⋮ → “Allow restricted settings”.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { openAppInfo(context) }) { Text("Open App info") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    openAccessibilitySettings(context)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) { Text("Not now") }
            },
        )
    }

    pickingSource?.let { isSource ->
        LanguagePickerDialog(
            title = if (isSource) "Screen is in" else "Translate screen to",
            selected = if (isSource) source else target,
            allowAuto = isSource,
            onSelect = { code ->
                if (isSource) {
                    prefs.screenSource.value = code
                } else {
                    prefs.screenTarget.value = code
                }
            },
            onDismiss = { pickingSource = null },
        )
    }
}

@Composable
private fun SectionHeader(icon: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MaterialIcon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StatusRow(running: Boolean) {
    val color = if (running) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    Surface(color = color, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MaterialIcon(
                if (running) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (running) "Screen translation is on" else "Screen translation is off",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * A platform TextView, because it shows the system "process text" actions (like AnyTranslate)
 * in its selection menu, which is exactly what we want to demo.
 */
@Composable
private fun SelectableSample(sample: String) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        AndroidView(
            factory = { context ->
                TextView(context).apply {
                    text = sample
                    textSize = 17f
                    setTextIsSelectable(true)
                }
            },
            update = { it.setTextColor(textColor) },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
    }
}

private fun openAccessibilitySettings(context: Context) {
    val component = ComponentName(context, TranslateAccessibilityService::class.java).flattenToString()
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Highlights our entry on many Android versions.
        .putExtra(":settings:fragment_args_key", component)
        .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", component) })
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        context.toast("Open Settings › Accessibility and turn on AnyTranslate.")
    }
}

private fun openAppInfo(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestAddTile(context: Context) {
    val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return
    statusBar.requestAddTileService(
        ComponentName(context, TranslateTileService::class.java),
        context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_translate),
        context.mainExecutor,
    ) { result ->
        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
            context.toast("The tile is already in Quick Settings.")
        }
    }
}
