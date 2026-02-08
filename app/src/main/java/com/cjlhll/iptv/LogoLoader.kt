package com.cjlhll.iptv

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object LogoLoader {
    private const val MAX_ENTRIES = 128
    private val client = OkHttpClient()
    private val cache = object : LinkedHashMap<String, androidx.compose.ui.graphics.ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, androidx.compose.ui.graphics.ImageBitmap>): Boolean {
            return size > MAX_ENTRIES
        }
    }

    private fun getCached(url: String): androidx.compose.ui.graphics.ImageBitmap? = synchronized(cache) { cache[url] }

    private fun putCached(url: String, bitmap: androidx.compose.ui.graphics.ImageBitmap) {
        synchronized(cache) { cache[url] = bitmap }
    }

    suspend fun load(url: String): androidx.compose.ui.graphics.ImageBitmap? {
        getCached(url)?.let { return it }

        val bytes = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.bytes()
            }
        } ?: return null

        val bitmap = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } ?: return null

        val imageBitmap = bitmap.asImageBitmap()
        putCached(url, imageBitmap)
        return imageBitmap
    }
}

@Composable
fun ChannelLogo(
    logoUrl: String?,
    fallbackTitle: String,
    modifier: Modifier = Modifier
) {
    var loaded by remember(logoUrl) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(logoUrl) {
        loaded = null
        val url = logoUrl?.trim().orEmpty()
        if (url.isBlank()) return@LaunchedEffect
        loaded = try {
            LogoLoader.load(url)
        } catch (_: Exception) {
            null
        }
    }

    val shape = RoundedCornerShape(10.dp)
    val bg = MaterialTheme.colorScheme.surfaceVariant

    if (loaded != null) {
        Image(
            bitmap = loaded!!,
            contentDescription = fallbackTitle,
            modifier = modifier.clip(shape)
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallbackTitle.take(1),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .padding(6.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
