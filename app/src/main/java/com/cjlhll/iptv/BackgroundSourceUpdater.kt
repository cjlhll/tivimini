package com.cjlhll.iptv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object BackgroundSourceUpdater {
    private const val TAG = "SourceRefresh"
    private const val EPG_STARTUP_DELAY_MS = 2_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient()
    private val playlistRefreshMutex = Mutex()

    @Volatile
    private var startupRefreshScheduled = false

    private val _playlistUpdated = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val playlistUpdated: SharedFlow<String> = _playlistUpdated.asSharedFlow()

    private val _epgUpdated = MutableSharedFlow<EpgData>(extraBufferCapacity = 1)
    val epgUpdated: SharedFlow<EpgData> = _epgUpdated.asSharedFlow()

    fun scheduleStartupRefresh(context: Context) {
        if (startupRefreshScheduled) return
        startupRefreshScheduled = true

        val appContext = context.applicationContext
        scope.launch {
            val liveSource = Prefs.getLiveSource(appContext)
            val epgSource = Prefs.getEpgSource(appContext)

            if (liveSource.isNotBlank()) {
                Log.i(TAG, "startup playlist refresh (immediate)")
                refreshPlaylist(appContext, liveSource)
            }

            if (epgSource.isNotBlank()) {
                delay(EPG_STARTUP_DELAY_MS)
                Log.i(TAG, "startup epg refresh (delayed)")
                refreshEpg(appContext, epgSource)
            }
        }
    }

    fun scheduleRefresh(context: Context, delayMs: Long = EPG_STARTUP_DELAY_MS) {
        val appContext = context.applicationContext
        scope.launch {
            if (delayMs > 0) delay(delayMs)
            refreshAll(appContext)
        }
    }

    suspend fun refreshPlaylistUrgent(context: Context): Boolean {
        val liveSource = Prefs.getLiveSource(context)
        if (liveSource.isBlank()) return false
        return refreshPlaylist(context, liveSource)
    }

    suspend fun refreshAll(context: Context) {
        val liveSource = Prefs.getLiveSource(context)
        val epgSource = Prefs.getEpgSource(context)
        if (liveSource.isBlank() && epgSource.isBlank()) return

        Log.i(TAG, "refresh all begin. live=${liveSource.isNotBlank()} epg=${epgSource.isNotBlank()}")

        if (liveSource.isNotBlank()) {
            refreshPlaylist(context, liveSource)
        }
        if (epgSource.isNotBlank()) {
            refreshEpg(context, epgSource)
        }

        Log.i(TAG, "refresh all finished")
    }

    suspend fun refreshPlaylist(context: Context, liveSource: String): Boolean {
        return playlistRefreshMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(liveSource).build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.w(TAG, "playlist download failed code=${response.code}")
                            return@withContext false
                        }
                        val content = response.body?.string()
                        if (content.isNullOrBlank()) {
                            Log.w(TAG, "playlist download empty body")
                            return@withContext false
                        }
                        val fileName = PlaylistCache.fileNameForSource(liveSource)
                        PlaylistCache.write(context, fileName, content)
                        Prefs.setPlaylistFileName(context, fileName)
                        _playlistUpdated.tryEmit(fileName)
                        Log.i(TAG, "playlist refreshed. bytes=${content.length}")
                        true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "playlist refresh failed: ${e.message}")
                    false
                }
            }
        }
    }

    suspend fun refreshEpg(context: Context, epgSource: String): EpgData? {
        return try {
            val data = EpgRepository.load(context, epgSource, forceRefresh = true)
            if (data != null) {
                Prefs.setLastEpgUpdateTime(context, System.currentTimeMillis())
                _epgUpdated.tryEmit(data)
                Log.i(TAG, "epg refreshed. programs=${data.programsByChannelId.size}")
            } else {
                Log.w(TAG, "epg refresh returned null")
            }
            data
        } catch (e: Exception) {
            Log.w(TAG, "epg refresh failed: ${e.message}")
            null
        }
    }
}
