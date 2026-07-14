package com.wuwaconfig.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.wuwaconfig.app.ui.components.GlassTopBar
import com.wuwaconfig.app.ui.theme.NeonCyan
import com.wuwaconfig.app.ui.theme.NeonPurple

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Suppress("ktlint:standard:function-naming")
@Composable
fun UserGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val htmlContent =
        remember {
            try {
                context.assets.open("user_guide.html").bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                "<html><body><h2>Failed to load user guide</h2></body></html>"
            }
        }
    Scaffold(
        topBar = {
            GlassTopBar(
                title = { Text("User Guide", color = NeonCyan) },
                accentColor = NeonCyan,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NeonPurple)
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.domStorageEnabled = true
                    loadDataWithBaseURL("https://wuwaconfig.local/", htmlContent, "text/html", "UTF-8", null)
                }
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        )
    }
}
