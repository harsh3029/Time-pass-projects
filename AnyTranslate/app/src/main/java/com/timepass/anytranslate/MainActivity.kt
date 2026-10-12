package com.timepass.anytranslate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.timepass.anytranslate.ui.screens.AnywhereScreen
import com.timepass.anytranslate.ui.screens.LiveScreen
import com.timepass.anytranslate.ui.screens.OfflineScreen
import com.timepass.anytranslate.ui.screens.TranslateScreen
import com.timepass.anytranslate.ui.theme.AnyTranslateTheme

class MainActivity : ComponentActivity() {

    /** A tab or text requested by an intent (tile, floating button, quick-translate sheet). */
    private var requestedTab by mutableStateOf<Int?>(null)
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            AnyTranslateTheme {
                AppScaffold(
                    requestedTab = requestedTab,
                    onTabHandled = { requestedTab = null },
                    sharedText = sharedText,
                    onSharedTextHandled = { sharedText = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.hasExtra(EXTRA_TAB)) requestedTab = intent.getIntExtra(EXTRA_TAB, TAB_TRANSLATE)
        intent.getStringExtra(EXTRA_TEXT)?.let {
            sharedText = it
            requestedTab = TAB_TRANSLATE
        }
    }

    companion object {
        const val EXTRA_TAB = "com.timepass.anytranslate.TAB"
        const val EXTRA_TEXT = "com.timepass.anytranslate.TEXT"
        const val TAB_TRANSLATE = 0
        const val TAB_LIVE = 1
        const val TAB_ANYWHERE = 2
        const val TAB_OFFLINE = 3
    }
}

private data class TabSpec(val label: String, val icon: Int)

private val tabs = listOf(
    TabSpec("Translate", R.drawable.ic_translate),
    TabSpec("Live", R.drawable.ic_mic),
    TabSpec("Anywhere", R.drawable.ic_layers),
    TabSpec("Offline", R.drawable.ic_download),
)

@Composable
private fun AppScaffold(
    requestedTab: Int?,
    onTabHandled: () -> Unit,
    sharedText: String?,
    onSharedTextHandled: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(MainActivity.TAB_TRANSLATE) }
    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            tab = requestedTab.coerceIn(tabs.indices)
            onTabHandled()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, spec ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(painterResource(spec.icon), contentDescription = null) },
                        label = { Text(spec.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            when (tab) {
                MainActivity.TAB_TRANSLATE -> TranslateScreen(sharedText, onSharedTextHandled)
                MainActivity.TAB_LIVE -> LiveScreen()
                MainActivity.TAB_ANYWHERE -> AnywhereScreen()
                else -> OfflineScreen()
            }
        }
    }
}
