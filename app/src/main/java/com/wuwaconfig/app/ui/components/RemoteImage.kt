package com.wuwaconfig.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
private val imageCache =
    object : LruCache<String, Bitmap>(maxMemoryKb / 6) {
        override fun sizeOf(
            key: String,
            bitmap: Bitmap,
        ): Int = bitmap.byteCount / 1024
    }

private suspend fun fetchBitmap(url: String): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val connection =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout = 12_000
                    instanceFollowRedirects = true
                }
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var bitmap by remember(url) { mutableStateOf(imageCache.get(url)) }
    var failed by remember(url) { mutableStateOf(false) }
    val resolvedScale = if (contentScale == ContentScale.Fit) ContentScale.Crop else contentScale

    LaunchedEffect(url) {
        if (bitmap != null) return@LaunchedEffect
        val fetched = fetchBitmap(url)
        if (fetched != null) {
            imageCache.put(url, fetched)
            bitmap = fetched
        } else {
            failed = true
        }
    }

    when {
        bitmap != null ->
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = resolvedScale,
            )
        else ->
            Box(
                modifier = modifier.background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center,
            ) {
                if (!failed) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = Color.White.copy(alpha = 0.4f))
                }
            }
    }
}
