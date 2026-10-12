package com.timepass.anytranslate.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timepass.anytranslate.R
import com.timepass.anytranslate.data.Languages
import com.timepass.anytranslate.translate.TranslationEngine

@Composable
fun LanguagePickerDialog(
    title: String,
    selected: String,
    allowAuto: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val downloaded by remember { TranslationEngine.get().downloaded }.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val options = remember(query, allowAuto) {
        val all = if (allowAuto) listOf(Languages.AUTO) + Languages.all else Languages.all
        if (query.isBlank()) {
            all
        } else {
            all.filter {
                Languages.displayName(it).contains(query, ignoreCase = true) ||
                    Languages.nativeName(it).contains(query, ignoreCase = true) ||
                    it.equals(query.trim(), ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
        ) {
            Column(Modifier.padding(vertical = 20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search languages") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(options, key = { it }) { code ->
                        val name = Languages.displayName(code)
                        val native = Languages.nativeName(code)
                        ListItem(
                            headlineContent = { Text(name) },
                            supportingContent = if (native != name) {
                                { Text(native) }
                            } else {
                                null
                            },
                            leadingContent = { RadioButton(selected = code == selected, onClick = null) },
                            trailingContent = if (code in downloaded) {
                                {
                                    Icon(
                                        painterResource(R.drawable.ic_offline),
                                        contentDescription = "Available offline",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else {
                                null
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                onSelect(code)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}
