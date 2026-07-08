package com.cjlhll.iptv

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.darkColorScheme
import androidx.compose.ui.graphics.Color as ComposeColor
import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private enum class PlaylistUpdateMode {
    /** 加载缓存，仅更新频道列表，不切换播放 */
    Initial,
    /** 后台静默刷新，仅 URL 失效时无感切换 */
    Background,
    /** 播放错误恢复，切换时显示 loading */
    Recovery,
}

class PlayerActivity : ComponentActivity() {
    private var onChannelStep: ((Int) -> Boolean)? = null
    private var setDrawerOpen: ((Boolean) -> Unit)? = null
    private var isDrawerOpen: Boolean = false
    private var drawerActiveColumn: DrawerColumn? = null
    private var focusToDrawerChannels: (() -> Unit)? = null
    private var setSettingsDrawerOpen: ((Boolean) -> Unit)? = null
    private var isSettingsDrawerOpen: Boolean = false
    private var setQualityDialogOpen: ((Boolean) -> Unit)? = null
    private var isQualityDialogOpen: Boolean = false
    private var setInfoBannerOpen: ((Boolean) -> Unit)? = null
    private var isInfoBannerOpen: Boolean = false
    private var isCatchupPlayback: Boolean = false
    private var onCatchupSeek: ((Long) -> Boolean)? = null
    private var onReturnToLive: (() -> Boolean)? = null
    private var lastBackPressTime: Long = 0
    private var lastBackPressTimeCatchup: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val videoUrl = intent.getStringExtra("VIDEO_URL") ?: ""
        val videoTitle = intent.getStringExtra("VIDEO_TITLE") ?: "Live"
        val playlistFileName = intent.getStringExtra("PLAYLIST_FILE_NAME") ?: Prefs.getPlaylistFileName(this)

        setContent {
            val colorScheme = darkColorScheme(
                primary = ComposeColor(0xFFBDBDBD),
                onPrimary = ComposeColor.Black,
                secondary = ComposeColor(0xFF757575),
                onSecondary = ComposeColor.Black,
                tertiary = ComposeColor(0xFF616161),
                onTertiary = ComposeColor.White,
                background = ComposeColor(0xFF121212),
                onBackground = ComposeColor.White,
                surface = ComposeColor(0xFF1E1E1E),
                onSurface = ComposeColor.White
            )

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = androidx.compose.ui.graphics.RectangleShape
                ) {
                    VideoPlayerScreen(
                        url = videoUrl,
                        title = videoTitle,
                        playlistFileName = playlistFileName,
                        setOnChannelStep = { onChannelStep = it },
                        setDrawerOpenController = { setDrawerOpen = it },
                        onDrawerOpenChanged = { isDrawerOpen = it },
                        onDrawerActiveColumnChanged = { drawerActiveColumn = it },
                        setFocusToDrawerChannelsController = { focusToDrawerChannels = it },
                        setSettingsDrawerOpenController = { setSettingsDrawerOpen = it },
                        onSettingsDrawerOpenChanged = { isSettingsDrawerOpen = it },
                        setQualityDialogOpenController = { setQualityDialogOpen = it },
                        onQualityDialogOpenChanged = { isQualityDialogOpen = it },
                        setInfoBannerOpenController = { setInfoBannerOpen = it },
                        onInfoBannerOpenChanged = { isInfoBannerOpen = it },
                        setOnCatchupSeek = { onCatchupSeek = it },
                        setOnReturnToLive = { onReturnToLive = it },
                        onCatchupPlaybackChanged = { isCatchupPlayback = it }
                    ) {
                        finish()
                    }
                }
            }
        }
    }

    private fun calculateSeekStep(repeatCount: Int): Long {
        return when {
            repeatCount == 0 -> 10_000L
            repeatCount < 15 -> 30_000L
            repeatCount < 40 -> 60_000L
            else -> 300_000L
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (!isDrawerOpen && !isSettingsDrawerOpen && !isQualityDialogOpen) {
                        setInfoBannerOpen?.invoke(true)
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (!isDrawerOpen && !isSettingsDrawerOpen && !isQualityDialogOpen) {
                        if (isCatchupPlayback) {
                            val step = calculateSeekStep(event.repeatCount)
                            if (onCatchupSeek?.invoke(-step) == true) return true
                        } else {
                            setDrawerOpen?.invoke(true)
                            return true
                        }
                    }
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!isDrawerOpen && !isSettingsDrawerOpen && !isQualityDialogOpen) {
                        if (isCatchupPlayback) {
                            val step = calculateSeekStep(event.repeatCount)
                            if (onCatchupSeek?.invoke(step) == true) return true
                        } else {
                            setSettingsDrawerOpen?.invoke(true)
                            return true
                        }
                    }
                }

                KeyEvent.KEYCODE_MENU -> {
                    if (!isDrawerOpen && !isSettingsDrawerOpen && !isQualityDialogOpen) {
                        setSettingsDrawerOpen?.invoke(true)
                        return true
                    }
                }

                KeyEvent.KEYCODE_BACK -> {
                    if (isQualityDialogOpen) {
                        setQualityDialogOpen?.invoke(false)
                        return true
                    }
                    if (isDrawerOpen) {
                        if (drawerActiveColumn != DrawerColumn.Channels) {
                            focusToDrawerChannels?.invoke()
                            lifecycleScope.launch {
                                delay(80)
                                setDrawerOpen?.invoke(false)
                            }
                        } else {
                            setDrawerOpen?.invoke(false)
                        }
                        return true
                    }
                    if (isSettingsDrawerOpen) {
                        setSettingsDrawerOpen?.invoke(false)
                        return true
                    }
                    if (isInfoBannerOpen) {
                        setInfoBannerOpen?.invoke(false)
                        return true
                    }

                    if (isCatchupPlayback) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastBackPressTimeCatchup > 2000) {
                            lastBackPressTimeCatchup = currentTime
                            android.widget.Toast.makeText(this, "再按一次返回直播", android.widget.Toast.LENGTH_SHORT).show()
                            return true
                        }
                        if (onReturnToLive?.invoke() == true) {
                            lastBackPressTimeCatchup = 0
                            return true
                        }
                        return true
                    }

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBackPressTime > 2000) {
                        lastBackPressTime = currentTime
                        android.widget.Toast.makeText(this, "再按一次退出app", android.widget.Toast.LENGTH_SHORT).show()
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!isDrawerOpen && !isSettingsDrawerOpen && !isQualityDialogOpen) {
                        if (isCatchupPlayback) {
                            setInfoBannerOpen?.invoke(true)
                            return true
                        }
                        if (onChannelStep?.invoke(-1) == true) return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!isDrawerOpen && !isSettingsDrawerOpen && !isQualityDialogOpen) {
                        if (isCatchupPlayback) {
                            setInfoBannerOpen?.invoke(true)
                            return true
                        }
                        if (onChannelStep?.invoke(1) == true) return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    url: String,
    title: String,
    playlistFileName: String?,
    setOnChannelStep: (((Int) -> Boolean)?) -> Unit,
    setDrawerOpenController: (((Boolean) -> Unit)?) -> Unit,
    onDrawerOpenChanged: (Boolean) -> Unit,
    onDrawerActiveColumnChanged: (DrawerColumn?) -> Unit,
    setFocusToDrawerChannelsController: (() -> Unit) -> Unit,
    setSettingsDrawerOpenController: (((Boolean) -> Unit)?) -> Unit,
    onSettingsDrawerOpenChanged: (Boolean) -> Unit,
    setQualityDialogOpenController: (((Boolean) -> Unit)?) -> Unit,
    onQualityDialogOpenChanged: (Boolean) -> Unit,
    setInfoBannerOpenController: (((Boolean) -> Unit)?) -> Unit,
    onInfoBannerOpenChanged: (Boolean) -> Unit,
    setOnCatchupSeek: (((Long) -> Boolean)?) -> Unit,
    setOnReturnToLive: ((() -> Boolean)?) -> Unit,
    onCatchupPlaybackChanged: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    
    var drawerOpen by remember { mutableStateOf(false) }
    var drawerActiveColumn by remember { mutableStateOf<DrawerColumn?>(null) }
    var settingsDrawerOpen by remember { mutableStateOf(false) }
    var qualityDialogOpen by remember { mutableStateOf(false) }
    var qualityDialogGroupIndex by remember { mutableIntStateOf(-1) }
    var infoBannerOpen by remember { mutableStateOf(false) }
    val channelSwitch = rememberChannelSwitchController()
    val playbackErrorEvents = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    var videoFormat by remember { mutableStateOf<androidx.media3.common.Format?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var channelGroups by remember { mutableStateOf<List<ChannelGroup>>(emptyList()) }
    var currentGroupIndex by remember { mutableIntStateOf(0) }
    var currentVariantIndex by remember { mutableIntStateOf(0) }
    var selectedGroup by remember { mutableStateOf("全部") }
    var epgData by remember { mutableStateOf<EpgData?>(null) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var nowProgramByUrl by remember { mutableStateOf<Map<String, NowProgramUi>>(emptyMap()) }
    var catchupRequest by remember { mutableStateOf<CatchupPlayRequest?>(null) }
    var catchupPositionMs by remember { mutableStateOf(0L) }
    var isSeekOverlayVisible by remember { mutableStateOf(false) }
    var catchupSeekPingAt by remember { mutableStateOf(0L) }

    val drawerLogoWidthPx = remember(density) { with(density) { 64.dp.roundToPx() } }
    val drawerLogoHeightPx = remember(density) { with(density) { 40.dp.roundToPx() } }
    val bannerLogoWidthPx = remember(density) { with(density) { 80.dp.roundToPx() } }
    val bannerLogoHeightPx = remember(density) { with(density) { 50.dp.roundToPx() } }

    val currentChannel = remember(channelGroups, currentGroupIndex, currentVariantIndex) {
        channelGroups.getOrNull(currentGroupIndex)
            ?.variants
            ?.getOrNull(currentVariantIndex)
            ?.channel
    }

    val allChannels = remember(channelGroups) {
        ChannelGrouper.allChannels(channelGroups)
    }

    val playingGroupIndex = remember(currentChannel?.url, channelGroups, currentGroupIndex) {
        ChannelGrouper.findGroupIndexByUrl(channelGroups, currentChannel?.url) ?: currentGroupIndex
    }

    val playingGroup = channelGroups.getOrNull(playingGroupIndex)
    val playingVariantIndex = remember(playingGroup, currentChannel?.url) {
        playingGroup?.let { ChannelGrouper.findVariantIndexByUrl(it, currentChannel?.url) } ?: 0
    }
    val epgLoadTag = remember { "EPG" }
    val shouldRefreshEpg = remember { mutableStateOf(false) }

    var heavyWorkEnabled by remember { mutableStateOf(false) }
    var firstDrawerOpenSeen by remember { mutableStateOf(false) }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        delay(1500)
        heavyWorkEnabled = true
    }

    fun initializePlayer() {
        if (player != null) return
        
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,
                120_000,
                1_000,
                2_000
            )
            .setBackBuffer(30_000, true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        val newPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderersFactory)
            .build()
            .apply {
                playWhenReady = true
                setWakeMode(C.WAKE_MODE_NETWORK)
            }
        
        val req = catchupRequest
        if (req != null) {
             newPlayer.setMediaItem(MediaItem.fromUri(req.catchupUrl))
        } else {
             val currentChannelUrl = currentChannel?.url ?: url
             newPlayer.setMediaItem(MediaItem.fromUri(currentChannelUrl))
        }
        
        newPlayer.prepare()
        newPlayer.play()
        player = newPlayer
    }

    fun releasePlayer() {
        player?.release()
        player = null
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                initializePlayer()
            } else if (event == Lifecycle.Event.ON_STOP) {
                releasePlayer()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            releasePlayer()
        }
    }

    DisposableEffect(player) {
        val p = player
        if (p != null) {
            var retryCount = 0
            val activity = context as? Activity

            fun setScreenOn(keepOn: Boolean) {
                val window = activity?.window ?: return
                if (keepOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            val listener = object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    setScreenOn(isPlaying)
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    videoFormat = p.videoFormat
                }

                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    videoFormat = p.videoFormat
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (catchupRequest != null) {
                        if (retryCount >= 3) return
                        retryCount += 1
                        val current = p.currentMediaItem ?: return
                        scope.launch {
                            delay(800)
                            p.setMediaItem(current)
                            p.prepare()
                            p.play()
                        }
                        return
                    }
                    playbackErrorEvents.tryEmit(Unit)
                }
            }

            p.addListener(listener)
            setScreenOn(p.isPlaying)
            onDispose {
                setScreenOn(false)
                p.removeListener(listener)
            }
        } else {
            onDispose { }
        }
    }

    val focusToChannels = {
        drawerActiveColumn = DrawerColumn.Channels
    }

    BindChannelSwitchLoading(channelSwitch, player)

    DisposableEffect(Unit) {
        setDrawerOpenController { drawerOpen = it }
        setFocusToDrawerChannelsController(focusToChannels)
        setSettingsDrawerOpenController { settingsDrawerOpen = it }
        setQualityDialogOpenController { qualityDialogOpen = it }
        setInfoBannerOpenController { infoBannerOpen = it }
        onDispose {
            setDrawerOpenController(null)
            setSettingsDrawerOpenController(null)
            setQualityDialogOpenController(null)
            setInfoBannerOpenController(null)
        }
    }

    LaunchedEffect(drawerOpen) {
        onDrawerOpenChanged(drawerOpen)
    }

    LaunchedEffect(drawerActiveColumn) {
        onDrawerActiveColumnChanged(drawerActiveColumn)
    }

    LaunchedEffect(settingsDrawerOpen) {
        onSettingsDrawerOpenChanged(settingsDrawerOpen)
    }

    LaunchedEffect(qualityDialogOpen) {
        onQualityDialogOpenChanged(qualityDialogOpen)
        if (!qualityDialogOpen) {
            qualityDialogGroupIndex = -1
        }
    }

    LaunchedEffect(infoBannerOpen, currentGroupIndex, currentVariantIndex) {
        onInfoBannerOpenChanged(infoBannerOpen)
        if (infoBannerOpen) {
            delay(5000)
            infoBannerOpen = false
        }
    }

    LaunchedEffect(catchupRequest) {
        onCatchupPlaybackChanged(catchupRequest != null)
    }

    LaunchedEffect(catchupSeekPingAt) {
        if (catchupSeekPingAt > 0) {
            isSeekOverlayVisible = true
            delay(1500)
            isSeekOverlayVisible = false
        }
    }

    LaunchedEffect(catchupRequest, player) {
        val p = player
        if (catchupRequest == null || p == null) {
            catchupPositionMs = 0L
            return@LaunchedEffect
        }
        while (true) {
            val req = catchupRequest ?: break
            val total = (req.endMillis - req.startMillis).coerceAtLeast(1L)
            catchupPositionMs = p.currentPosition.coerceIn(0L, total)
            delay(500)
        }
    }

    suspend fun applyPlaylistContent(
        content: String,
        preserveCurrentUrl: String?,
        mode: PlaylistUpdateMode = PlaylistUpdateMode.Initial,
    ) {
        val previousUrl = preserveCurrentUrl ?: currentChannel?.url ?: url
        val preserveTitle = channelGroups.getOrNull(currentGroupIndex)?.displayTitle ?: title
        val previousGroups = channelGroups
        val previousGroupIndex = currentGroupIndex

        val (parsedGroups, groupIndex, variantIndex) = withContext(Dispatchers.Default) {
            val parsedChannels = M3uParser.parse(content)
            val groups = ChannelGrouper.group(parsedChannels)
            val (gIdx, vIdx) = if (groups.isEmpty()) {
                0 to 0
            } else if (!previousUrl.isNullOrBlank() && ChannelGrouper.containsUrl(groups, previousUrl)) {
                val gi = ChannelGrouper.findGroupIndexByUrl(groups, previousUrl)!!
                gi to ChannelGrouper.findVariantIndexByUrl(groups[gi], previousUrl)
            } else {
                ChannelGrouper.findBestGroupVariant(
                    groups,
                    previousUrl,
                    preserveTitle,
                    previousGroups,
                    previousGroupIndex,
                )
            }
            Triple(groups, gIdx, vIdx)
        }
        if (parsedGroups.isEmpty()) return

        var targetGroupIndex = groupIndex.coerceIn(0, parsedGroups.lastIndex)
        var targetVariantIndex = variantIndex.coerceIn(
            0,
            parsedGroups[targetGroupIndex].variants.lastIndex
        )

        val urlMissing = !previousUrl.isNullOrBlank() &&
            !ChannelGrouper.containsUrl(parsedGroups, previousUrl)
        if (urlMissing && mode != PlaylistUpdateMode.Initial) {
            ChannelGrouper.findRecoveryVariant(
                parsedGroups,
                previousUrl,
                preserveTitle,
                previousGroups,
                previousGroupIndex,
            )?.let { (gIdx, vIdx) ->
                targetGroupIndex = gIdx
                targetVariantIndex = vIdx
            }
        }

        channelGroups = parsedGroups
        currentGroupIndex = targetGroupIndex
        currentVariantIndex = targetVariantIndex

        val newChannel = parsedGroups[targetGroupIndex].variants[targetVariantIndex].channel
        val shouldSwitchPlayback = catchupRequest == null &&
            player != null &&
            newChannel.url != previousUrl &&
            mode != PlaylistUpdateMode.Initial

        if (shouldSwitchPlayback) {
            if (mode == PlaylistUpdateMode.Recovery) {
                channelSwitch.show()
            }
            player?.let { p ->
                p.setMediaItem(MediaItem.fromUri(newChannel.url))
                p.prepare()
                p.play()
            }
            Prefs.setLastChannel(context, newChannel.url, newChannel.title)
            Log.i(
                "PlayerActivity",
                if (mode == PlaylistUpdateMode.Background) {
                    "silent recovered playback url: ${newChannel.url}"
                } else {
                    "recovered playback url: ${newChannel.url}"
                }
            )
        }

        while (!heavyWorkEnabled) delay(200)
        val urls = buildPrefetchLogoUrls(displayChannelsFromGroups(parsedGroups), currentGroupIndex)
        scope.launch {
            LogoLoader.prefetch(context, urls, drawerLogoWidthPx, drawerLogoHeightPx)
        }
        scope.launch {
            delay(800)
            LogoLoader.prefetch(context, urls, bannerLogoWidthPx, bannerLogoHeightPx)
        }
    }

    LaunchedEffect(playlistFileName, url, title) {
        val liveSource = Prefs.getLiveSource(context)
        val cacheFileName = when {
            !playlistFileName.isNullOrBlank() -> playlistFileName
            liveSource.isNotBlank() -> PlaylistCache.fileNameForSource(liveSource)
            else -> null
        }

        if (!cacheFileName.isNullOrBlank()) {
            val content = withContext(Dispatchers.IO) { PlaylistCache.read(context, cacheFileName) }
            if (content != null) {
                applyPlaylistContent(content, preserveCurrentUrl = url, mode = PlaylistUpdateMode.Initial)
            }
        }
    }

    LaunchedEffect(Unit) {
        BackgroundSourceUpdater.playlistUpdated.collectLatest { fileName ->
            val expected = Prefs.getPlaylistFileName(context) ?: return@collectLatest
            if (fileName != expected) return@collectLatest
            val content = withContext(Dispatchers.IO) { PlaylistCache.read(context, fileName) } ?: return@collectLatest
            applyPlaylistContent(
                content,
                preserveCurrentUrl = currentChannel?.url ?: url,
                mode = PlaylistUpdateMode.Background,
            )
        }
    }

    LaunchedEffect(Unit) {
        playbackErrorEvents.collect {
            if (catchupRequest != null) return@collect
            val failedUrl = currentChannel?.url ?: url
            channelSwitch.show()

            val refreshed = BackgroundSourceUpdater.refreshPlaylistUrgent(context)
            if (refreshed) {
                val fileName = Prefs.getPlaylistFileName(context) ?: return@collect
                val content = withContext(Dispatchers.IO) { PlaylistCache.read(context, fileName) } ?: return@collect
                applyPlaylistContent(content, preserveCurrentUrl = failedUrl, mode = PlaylistUpdateMode.Recovery)
                return@collect
            }

            val group = channelGroups.getOrNull(currentGroupIndex) ?: return@collect
            val variants = group.variants
            if (variants.size <= 1) return@collect
            val start = (currentVariantIndex + 1) % variants.size
            for (offset in variants.indices) {
                val idx = (start + offset) % variants.size
                val variant = variants[idx]
                if (variant.channel.url != failedUrl) {
                    val ch = variant.channel
                    catchupRequest = null
                    player?.let { p ->
                        p.setMediaItem(MediaItem.fromUri(ch.url))
                        p.prepare()
                        p.play()
                    }
                    currentVariantIndex = idx
                    Prefs.setLastChannel(context, ch.url, ch.title)
                    return@collect
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val epgSource = Prefs.getEpgSource(context)
        if (epgSource.isNotBlank()) {
            val cachedEpg = EpgRepository.load(context, epgSource, forceRefresh = false)
            if (cachedEpg != null) {
                epgData = cachedEpg
                Log.i(epgLoadTag, "cache loaded. programs=${cachedEpg.programsByChannelId.size}")
            }

            launch {
                BackgroundSourceUpdater.epgUpdated.collect { data ->
                    epgData = data
                    Log.i(epgLoadTag, "background updated. programs=${data.programsByChannelId.size}")
                }
            }

            var lastAttemptTime = System.currentTimeMillis()
            while (true) {
                val now = System.currentTimeMillis()

                if (!shouldRefreshEpg.value && (now - lastAttemptTime < 5 * 60 * 1000L)) {
                    delay(10_000L)
                    continue
                }

                val lastEpgUpdate = Prefs.getLastEpgUpdateTime(context)
                val zone = ZoneId.systemDefault()
                val lastDate = Instant.ofEpochMilli(lastEpgUpdate).atZone(zone).toLocalDate()
                val nowDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

                val isDifferentDay = !lastDate.isEqual(nowDate)
                val isOver6Hours = (now - lastEpgUpdate) > 6 * 3600 * 1000L

                if (shouldRefreshEpg.value || isDifferentDay || isOver6Hours) {
                    lastAttemptTime = now
                    val newEpg = EpgRepository.load(context, epgSource, forceRefresh = true)
                    if (newEpg != null) {
                        epgData = newEpg
                        Prefs.setLastEpgUpdateTime(context, System.currentTimeMillis())
                        Log.i(epgLoadTag, "network refreshed. programs=${newEpg.programsByChannelId.size}")
                        if (shouldRefreshEpg.value) {
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "EPG更新成功", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Log.w(epgLoadTag, "network refresh failed")
                    }
                    shouldRefreshEpg.value = false
                }
                delay(600_000L)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(30_000)
        }
    }

    val groups = remember(channelGroups) {
        ChannelGrouper.sortedDrawerGroups(channelGroups)
    }

    val filteredChannels = remember(channelGroups, selectedGroup) {
        val display = displayChannelsFromGroups(channelGroups)
        if (selectedGroup == "全部") display
        else {
            channelGroups.mapIndexedNotNull { index, group ->
                if (ChannelGrouper.displayGroupName(group) == selectedGroup) display.getOrNull(index) else null
            }
        }
    }

    LaunchedEffect(epgData, nowMillis, channelGroups, selectedGroup) {
        val data = epgData
        val groups = if (selectedGroup == "全部") {
            channelGroups
        } else {
            channelGroups.filter {
                ChannelGrouper.displayGroupName(it) == selectedGroup
            }
        }
        if (data == null || groups.isEmpty()) {
            nowProgramByUrl = emptyMap()
            return@LaunchedEffect
        }

        val map = withContext(Dispatchers.Default) {
            buildMap {
                for (group in groups) {
                    val displayChannel = group.defaultChannel
                    val t = data.nowProgramTitle(displayChannel, nowMillis)
                    if (!t.isNullOrBlank()) {
                        val current = data.nowProgram(displayChannel, nowMillis)
                        val progress = current?.let {
                            val duration = (it.endMillis - it.startMillis).toFloat()
                            if (duration <= 0f) null
                            else ((nowMillis - it.startMillis).toFloat() / duration).coerceIn(0f, 1f)
                        }
                        val ui = NowProgramUi(title = t, progress = progress)
                        for (variant in group.variants) {
                            put(variant.channel.url, ui)
                        }
                    }
                }
            }
        }
        nowProgramByUrl = map
    }

    fun startPlayback(url: String, showLoading: Boolean = true) {
        val isSameLiveUrl = catchupRequest == null && url == currentChannel?.url
        if (showLoading && !isSameLiveUrl) {
            channelSwitch.show()
        }
        player?.let { p ->
            p.setMediaItem(MediaItem.fromUri(url))
            p.prepare()
            p.play()
        }
    }

    fun playGroupVariant(groupIndex: Int, variantIndex: Int) {
        val group = channelGroups.getOrNull(groupIndex) ?: return
        val variant = group.variants.getOrNull(variantIndex) ?: return
        val channel = variant.channel
        catchupRequest = null
        startPlayback(channel.url)
        currentGroupIndex = groupIndex
        currentVariantIndex = variantIndex
        Prefs.setLastChannel(context, channel.url, channel.title)
    }

    fun playChannel(channel: Channel) {
        channelGroups.forEachIndexed { groupIndex, group ->
            group.variants.forEachIndexed { variantIndex, variant ->
                if (variant.channel.url == channel.url) {
                    playGroupVariant(groupIndex, variantIndex)
                    return
                }
            }
        }
    }

    DisposableEffect(player, channelGroups, currentGroupIndex, currentVariantIndex, catchupRequest) {
        setOnCatchupSeek { deltaMs ->
            val p = player ?: return@setOnCatchupSeek false
            val req = catchupRequest ?: return@setOnCatchupSeek false
            if (!p.isCurrentMediaItemSeekable) return@setOnCatchupSeek false
            val total = (req.endMillis - req.startMillis).coerceAtLeast(1L)
            val target = (p.currentPosition + deltaMs).coerceIn(0L, total)
            p.seekTo(target)
            catchupSeekPingAt = System.currentTimeMillis()
            catchupPositionMs = target
            true
        }
        setOnReturnToLive {
            val req = catchupRequest ?: return@setOnReturnToLive false
            val channel = allChannels.firstOrNull { it.url == req.liveUrl }
            if (channel != null) {
                playChannel(channel)
            } else {
                catchupRequest = null
                startPlayback(req.liveUrl)
            }
            true
        }
        onDispose {
            setOnCatchupSeek(null)
            setOnReturnToLive(null)
        }
    }

    DisposableEffect(channelGroups, currentGroupIndex, player) {
        setOnChannelStep { direction ->
            if (channelGroups.size < 2) return@setOnChannelStep false
            if (drawerOpen || settingsDrawerOpen || qualityDialogOpen) return@setOnChannelStep false
            val size = channelGroups.size
            val nextGroupIndex = (currentGroupIndex + direction).let { raw ->
                ((raw % size) + size) % size
            }
            val group = channelGroups[nextGroupIndex]
            playGroupVariant(nextGroupIndex, group.defaultVariantIndex)
            infoBannerOpen = true
            true
        }

        onDispose {
            setOnChannelStep(null)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (player != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        PlayerView(it).apply {
                            this.player = player
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            setKeepContentOnPlayerReset(true)
                            setShutterBackgroundColor(Color.TRANSPARENT)
                            keepScreenOn = true
                            isFocusable = false
                            isFocusableInTouchMode = false
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        }
                    },
                    update = {
                        it.player = player
                    }
                )

                ChannelSwitchOverlay(
                    controller = channelSwitch,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                )
            }
        }

        PlayerDrawer(
            visible = drawerOpen,
            groups = groups,
            selectedGroup = selectedGroup,
            channels = filteredChannels,
            selectedChannelUrl = channelGroups.getOrNull(currentGroupIndex)?.defaultChannel?.url,
            nowProgramByChannelUrl = nowProgramByUrl,
            epgData = epgData,
            nowMillis = nowMillis,
            onSelectGroup = { selectedGroup = it },
            onSelectChannel = { channel ->
                val groupIndex = channelGroups.indexOfFirst { it.defaultChannel.url == channel.url }
                if (groupIndex >= 0) {
                    playGroupVariant(groupIndex, channelGroups[groupIndex].defaultVariantIndex)
                }
            },
            onPlayProgram = { req ->
                Log.d("PlayerActivity", "onPlayProgram called with url: ${req.catchupUrl}")
                catchupRequest = req
                startPlayback(req.catchupUrl)
                drawerOpen = false
            },
            onClose = { drawerOpen = false },
            onActiveColumnChanged = { drawerActiveColumn = it },
            requestedActiveColumn = drawerActiveColumn
        )

        val qualityDialogGroup = channelGroups.getOrNull(
            if (qualityDialogGroupIndex >= 0) qualityDialogGroupIndex else playingGroupIndex
        )
        val qualityVariants = qualityDialogGroup?.variants?.takeIf { it.size > 1 }?.mapIndexed { index, variant ->
            val selectedIndex = if (qualityDialogOpen && qualityDialogGroupIndex >= 0) {
                ChannelGrouper.findVariantIndexByUrl(qualityDialogGroup, currentChannel?.url)
            } else {
                playingVariantIndex
            }
            QualityVariantUi(
                label = variant.qualityLabel,
                selected = index == selectedIndex
            )
        }.orEmpty()

        SettingsDrawer(
            visible = settingsDrawerOpen,
            onSourceConfigClick = {
                val intent = Intent(context, MainActivity::class.java)
                context.startActivity(intent)
            },
            onEpgSettingsClick = {
                shouldRefreshEpg.value = true
                settingsDrawerOpen = false
                android.widget.Toast.makeText(context, "开始更新EPG...", android.widget.Toast.LENGTH_SHORT).show()
            },
            qualityMenuEnabled = playingGroup?.variants?.size?.let { it > 1 } == true,
            onQualityMenuClick = {
                settingsDrawerOpen = false
                qualityDialogGroupIndex = playingGroupIndex
                qualityDialogOpen = true
            },
            onCheckUpdateClick = {
                settingsDrawerOpen = false
                android.widget.Toast.makeText(context, "正在检查更新...", android.widget.Toast.LENGTH_SHORT).show()
                scope.launch {
                    val info = UpdateChecker.checkUpdateAndGetInfo(context)
                    if (info != null && info.hasUpdate) {
                        updateInfo = info
                        showUpdateDialog = true
                    } else {
                        android.widget.Toast.makeText(context, "已是最新版本", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onClose = { settingsDrawerOpen = false }
        )

        QualitySelectorDialog(
            visible = qualityDialogOpen,
            channelTitle = qualityDialogGroup?.displayTitle.orEmpty(),
            variants = qualityVariants,
            onSelect = { variantIndex ->
                val groupIndex = if (qualityDialogGroupIndex >= 0) qualityDialogGroupIndex else playingGroupIndex
                playGroupVariant(groupIndex, variantIndex)
            },
            onClose = { qualityDialogOpen = false }
        )

        if (showUpdateDialog && updateInfo != null) {
            UpdateDialog(
                updateInfo = updateInfo!!,
                onDismiss = {
                    showUpdateDialog = false
                    updateInfo = null
                }
            )
        }

        val req = catchupRequest

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Bottom
            ) {
                val catchupConfig = if (req != null) {
                    CatchupConfiguration(
                        startMillis = req.startMillis,
                        endMillis = req.endMillis,
                        positionMs = catchupPositionMs
                    )
                } else null

                val displayChannel = if (req != null) {
                    allChannels.firstOrNull { it.url == req.liveUrl }
                } else {
                    currentChannel
                }

                val bannerChannel = displayChannel?.let { ch ->
                    val cleanTitle = channelGroups.getOrNull(playingGroupIndex)?.displayTitle
                        ?: EpgNormalize.displayName(ch.title)
                    if (cleanTitle == ch.title) ch else ch.copy(title = cleanTitle)
                }

                val displayTitle = if (req != null) {
                    "${req.programTitle} (回看中)"
                } else {
                    displayChannel?.let { nowProgramByUrl[it.url]?.title }
                }

                val displayProgress = if (req != null) {
                    null
                } else {
                    displayChannel?.let { nowProgramByUrl[it.url]?.progress }
                }

                val displayNumber = if (req != null) {
                    val liveUrl = req.liveUrl
                    val groupIdx = channelGroups.indexOfFirst { group ->
                        group.variants.any { it.channel.url == liveUrl }
                    }
                    if (groupIdx >= 0) groupIdx + 1 else 0
                } else {
                    currentGroupIndex + 1
                }

                ChannelInfoBanner(
                    visible = infoBannerOpen || isSeekOverlayVisible,
                    channel = bannerChannel,
                    programTitle = displayTitle,
                    programProgress = displayProgress,
                    channelNumber = displayNumber,
                    videoFormat = videoFormat,
                    catchupConfiguration = catchupConfig,
                    animate = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun displayChannelsFromGroups(groups: List<ChannelGroup>): List<Channel> {
    return ChannelGrouper.displayChannels(groups)
}

private fun buildPrefetchLogoUrls(channels: List<Channel>, currentIndex: Int): List<String> {
    if (channels.isEmpty()) return emptyList()

    val maxUrls = 220
    val out = ArrayList<String>(minOf(maxUrls, channels.size))
    val seen = HashSet<String>(minOf(maxUrls, channels.size))

    fun addAt(index: Int) {
        if (out.size >= maxUrls) return
        val url = channels.getOrNull(index)?.logoUrl?.trim().orEmpty()
        if (url.isNotBlank() && seen.add(url)) out.add(url)
    }

    addAt(currentIndex)
    for (delta in 1..8) {
        addAt(currentIndex - delta)
        addAt(currentIndex + delta)
    }

    val head = minOf(120, channels.size)
    for (i in 0 until head) addAt(i)

    return out
}

private val catchupTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun CatchupProgressBar(
    startMillis: Long,
    endMillis: Long,
    positionMs: Long,
    modifier: Modifier = Modifier,
    isBottomPaddingNeeded: Boolean = false
) {
    val total = (endMillis - startMillis).coerceAtLeast(1L)
    val progress = (positionMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val zone = ZoneId.systemDefault()
    val startText = catchupTimeFormatter.format(Instant.ofEpochMilli(startMillis).atZone(zone))
    val endText = catchupTimeFormatter.format(Instant.ofEpochMilli(endMillis).atZone(zone))

    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    val bottomPadding = if (isBottomPaddingNeeded) 20.dp else 4.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor, shape)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = bottomPadding)
        ) {
            androidx.tv.material3.Text(
                text = startText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
            ) {
                // Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.Center)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f), RoundedCornerShape(2.dp))
                )
                
                // Progress Fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .align(Alignment.CenterStart)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                )

                // Dot
                val bias = if (progress.isNaN()) -1f else (progress * 2) - 1
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(BiasAlignment(bias.coerceIn(-1f, 1f), 0f))
                        .background(ComposeColor.White, CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            androidx.tv.material3.Text(
                text = endText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )
        }
    }
}
