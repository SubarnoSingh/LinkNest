package com.linknest.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.activity.viewModels
import com.linknest.app.presentation.MainViewModel
import com.linknest.app.ui.LinkNestApp
import com.linknest.app.ui.theme.LinkNestTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val app = application as LinkNestApplication
        MainViewModel.Factory(app.repository, app.metadataFetcher, app.backupService)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        handleIntent(intent)
        setContent {
            LinkNestTheme { LinkNestApp(viewModel) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val title = intent.getStringExtra(Intent.EXTRA_TITLE)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.dataString
                viewModel.receiveSharedContent(listOfNotNull(title, text).joinToString("\n").ifBlank { null })
            }
            Intent.ACTION_VIEW -> viewModel.receiveSharedContent(intent.dataString, intent.dataString)
        }
    }
}
