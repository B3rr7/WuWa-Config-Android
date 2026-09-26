package com.wuwaconfig.app.ui.screens

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.wuwaconfig.app.ui.components.GlassTopBar
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.NeonCyan
import com.wuwaconfig.app.ui.theme.NeonPurple

/**
 * Synthetic origin for the local guide. It is never resolved — `loadDataWithBaseURL`
 * only needs a base for relative URLs and the WebView's same-origin checks. It is NOT
 * a real site and must never be dialled.
 */
private const val BASE_URL = "https://wuwaconfig.local/"

/** Hands an external link to the system browser. Never let the guide WebView follow it. */
private fun openExternally(
    ctx: android.content.Context,
    url: String,
) {
    // Only ever hand off real web URLs. Refuse everything else (intent://, file://,
    // javascript:, custom schemes) so a crafted link cannot reach another component.
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    if (uri.scheme != "http" && uri.scheme != "https") return
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        // No browser installed. Swallow: a dead link must not crash the guide.
    } catch (_: SecurityException) {
    }
}

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
    GradientBackground {
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
            containerColor = Color.Transparent,
        ) { padding ->
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        // The guide is a LOCAL asset. The default WebViewClient would
                        // happily navigate in-place when a link is tapped, loading a
                        // third-party page (discord.gg, github.com, youtube, t.me,
                        // shizuku.rikka.app) inside our WebView with JavaScript enabled
                        // and the app's cookie jar. Hand any http(s) navigation to the
                        // system browser instead and keep this WebView on the asset.
                        webViewClient =
                            object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    val url = request.url?.toString().orEmpty()
                                    return if (url.startsWith(BASE_URL)) {
                                        false
                                    } else {
                                        openExternally(ctx, url)
                                        true
                                    }
                                }

                                // Pre-N path (API < 24 still routes here on some OEM WebViews).
                                @Deprecated("Superseded by the WebResourceRequest overload")
                                @Suppress("OVERRIDE_DEPRECATION")
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    url: String?,
                                ): Boolean {
                                    val target = url.orEmpty()
                                    return if (target.isEmpty() || target.startsWith(BASE_URL)) {
                                        false
                                    } else {
                                        openExternally(ctx, target)
                                        true
                                    }
                                }
                            }
                        settings.javaScriptEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        // A static local page needs no DOM storage, and enabling it
                        // would leave guide state persisting in the WebView profile.
                        settings.domStorageEnabled = false
                        // Nothing in the guide is a local file or a content:// resource.
                        // Deny both so a hypothetical injected reference cannot reach
                        // app-private storage through the WebView.
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        // Deprecated no-ops on API 30+ (allowFileAccess=false already
                        // covers it), but on API 26-29 these are the only switches that
                        // stop a file:// document from reaching app-private storage, and
                        // minSdk is 26. Keeping them is deliberate.
                        @Suppress("DEPRECATION")
                        run {
                            settings.allowFileAccessFromFileURLs = false
                            settings.allowUniversalAccessFromFileURLs = false
                        }
                        // No reason for the guide to sniff the network.
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        loadDataWithBaseURL(BASE_URL, htmlContent, "text/html", "UTF-8", null)
                    }
                },
                // A WebView holds the Activity context and is never GC'd on its
                // own: without this every visit to Settings -> User Guide leaked
                // one (with JavaScript enabled).
                onRelease = { webView ->
                    webView.stopLoading()
                    (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                    webView.destroy()
                },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
            )
        }
    }
}
