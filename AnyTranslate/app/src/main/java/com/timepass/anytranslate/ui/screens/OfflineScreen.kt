package com.timepass.anytranslate.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.nl.translate.TranslateLanguage
import com.timepass.anytranslate.R
import com.timepass.anytranslate.data.Languages
import com.timepass.anytranslate.translate.TranslationEngine
import com.timepass.anytranslate.ui.components.ScreenTitle
import com.timepass.anytranslate.ui.components.toast
import kotlinx.coroutines.launch

@Composable
fun OfflineScreen() {
    val context = LocalContext.current
    val engine = remember { TranslationEngine.get() }
    val downloaded by engine.downloaded.collectAsStateWithLifecycle()
    val downloading by engine.downloading.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { engine.refreshDownloaded() }

    // Downloaded languages first, then the rest alphabetically.
    val languages = remember(query, downloaded) {
        Languages.all
            .filter {
                query.isBlank() ||
                    Languages.displayName(it).contains(query, ignoreCase = true) ||
                    Languages.nativeName(it).contains(query, ignoreCase = true)
            }
            .sortedBy { if (it in downloaded) 0 else 1 }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            ScreenTitle(
                "Offline languages",
                "Each language pack is about 30 MB. Once it's downloaded, translation works without internet " +
                    "and your text never leaves the phone. English is built in.",
                Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search languages") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
        items(languages, key = { it }) { code ->
            val isDownloaded = code in downloaded
            val isDownloading = code in downloading
            ListItem(
                headlineContent = { Text(Languages.displayName(code)) },
                supportingContent = {
                    Text(
                        when {
                            isDownloading -> "Downloading…"
                            code == TranslateLanguage.ENGLISH -> "Built in"
                            isDownloaded -> "Downloaded · ${Languages.nativeName(code)}"
                            else -> Languages.nativeName(code)
                        }
                    )
                },
                leadingContent = if (isDownloaded) {
                    {
                        Icon(
                            painterResource(R.drawable.ic_offline),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    null
                },
                trailingContent = {
                    when {
                        isDownloading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        code == TranslateLanguage.ENGLISH -> Unit
                        isDownloaded -> IconButton(onClick = {
                            scope.launch {
                                runCatching { engine.deleteModel(code) }
                                    .onFailure { context.toast("Couldn't delete: ${it.message}") }
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${Languages.displayName(code)}")
                        }
                        else -> IconButton(onClick = {
                            scope.launch {
                                runCatching { engine.ensureModel(code) }
                                    .onFailure { context.toast(it.message ?: "Download failed") }
                            }
                        }) {
                            Icon(painterResource(R.drawable.ic_download), contentDescription = "Download ${Languages.displayName(code)}")
                        }
                    }
                },
            )
        }
    }
}
