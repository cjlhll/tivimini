package com.cjlhll.iptv

import android.content.Context
import java.io.File
import java.security.MessageDigest

object EpgCache {
    fun fileNameForSource(sourceUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(sourceUrl.toByteArray(Charsets.UTF_8))
        val hex = buildString(digest.size * 2) {
            for (b in digest) {
                append(((b.toInt() shr 4) and 0xF).toString(16))
                append((b.toInt() and 0xF).toString(16))
            }
        }
        return "epg_$hex.xmltv"
    }

    fun writeBytes(context: Context, fileName: String, content: ByteArray): File {
        val file = File(context.filesDir, fileName)
        file.writeBytes(content)
        return file
    }

    fun readBytes(context: Context, fileName: String): ByteArray? {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return null
        return file.readBytes()
    }
}

