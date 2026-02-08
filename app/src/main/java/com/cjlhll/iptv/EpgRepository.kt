package com.cjlhll.iptv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

object EpgRepository {
    private const val TAG = "EPG"

    suspend fun load(context: Context, epgSourceUrl: String): EpgData? {
        if (epgSourceUrl.isBlank()) return null

        return withContext(Dispatchers.IO) {
            val cacheFileName = EpgCache.fileNameForSource(epgSourceUrl)
            val cachedBytes = EpgCache.readBytes(context, cacheFileName)

            if (cachedBytes != null) {
                val parsed = parseBytes(cachedBytes)
                if (parsed != null) return@withContext parsed
            }

            val client = OkHttpClient()
            val request = Request.Builder().url(epgSourceUrl).build()

            val downloaded = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.bytes()
            } ?: return@withContext null

            EpgCache.writeBytes(context, cacheFileName, downloaded)
            parseBytes(downloaded)
        }
    }

    private fun parseBytes(bytes: ByteArray): EpgData? {
        return try {
            val input = ByteArrayInputStream(bytes)
            val stream = if (looksLikeGzip(bytes)) GZIPInputStream(input) else input
            stream.use { XmlTvParser.parse(it) }
        } catch (e: Exception) {
            Log.w(TAG, "parse xmltv failed: ${e.message}")
            null
        }
    }

    private fun looksLikeGzip(bytes: ByteArray): Boolean {
        if (bytes.size < 2) return false
        return bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
    }
}

