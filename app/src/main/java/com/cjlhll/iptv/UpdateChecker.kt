package com.cjlhll.iptv

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val REPO_OWNER = "cjlhll"
    private const val REPO_NAME = "tivimini"
    private const val RELEASES_URL = "https://github.com/$REPO_OWNER/$REPO_NAME/releases"

    data class UpdateInfo(
        val latestTag: String,
        val currentVersion: String,
        val hasUpdate: Boolean,
        val downloadUrl: String
    )

    suspend fun checkUpdate(context: Context): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val currentVersion = getCurrentVersion(context)
                Log.d(TAG, "Current version: $currentVersion")

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(RELEASES_URL)
                    .header("User-Agent", "Mozilla/5.0 (Android) IPTV")
                    .build()

                Log.d(TAG, "Fetching: $RELEASES_URL")
                val response = client.newCall(request).execute()
                Log.d(TAG, "Response code: ${response.code}")

                if (!response.isSuccessful) {
                    Log.e(TAG, "Request failed: ${response.code}")
                    return@withContext null
                }

                val html = response.body?.string() ?: run {
                    Log.e(TAG, "Empty response body")
                    return@withContext null
                }

                val latestTag = extractLatestTag(html)
                if (latestTag == null) {
                    Log.e(TAG, "Failed to extract latest tag")
                    return@withContext null
                }
                Log.d(TAG, "Latest tag: $latestTag")

                val hasUpdate = compareVersions(currentVersion, latestTag) < 0
                Log.d(TAG, "Has update: $hasUpdate")

                val downloadUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$latestTag/TiviMini-$latestTag.apk"
                Log.d(TAG, "Download URL: $downloadUrl")

                UpdateInfo(
                    latestTag = latestTag,
                    currentVersion = currentVersion,
                    hasUpdate = hasUpdate,
                    downloadUrl = downloadUrl
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error checking update: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }

    private fun extractLatestTag(html: String): String? {
        val pattern = Pattern.compile("/releases/tag/([^\\s\"']+)")
        val matcher = pattern.matcher(html)
        return if (matcher.find()) {
            matcher.group(1)
        } else null
    }

    private fun getCurrentVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            val version = packageInfo.versionName ?: "1.0"
            Log.d(TAG, "getCurrentVersion: $version")
            version
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Package not found: ${e.message}")
            "1.0"
        }
    }

    private fun compareVersions(current: String, latest: String): Int {
        val currentClean = current.removePrefix("v").removePrefix("V")
        val latestClean = latest.removePrefix("v").removePrefix("V")

        Log.d(TAG, "compareVersions: $currentClean vs $latestClean")

        val currentParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latestClean.split(".").mapNotNull { it.toIntOrNull() }

        Log.d(TAG, "currentParts: $currentParts, latestParts: $latestParts")

        val maxLen = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLen) {
            val c = currentParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (c < l) return -1
            if (c > l) return 1
        }
        return 0
    }
}
