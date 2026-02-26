package com.cjlhll.iptv

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text as M3Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colorScheme = darkColorScheme(
                primary = Color(0xFFBDBDBD),
                onPrimary = Color.Black,
                secondary = Color(0xFF757575),
                onSecondary = Color.Black,
                tertiary = Color(0xFF616161),
                onTertiary = Color.White,
                background = Color(0xFF121212),
                onBackground = Color.White,
                surface = Color(0xFF1E1E1E),
                onSurface = Color.White
            )
            
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = androidx.compose.ui.graphics.RectangleShape
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Load initial values from Prefs
    val savedLiveSource = remember { Prefs.getLiveSource(context) }
    val savedEpgSource = remember { Prefs.getEpgSource(context) }
    
    var liveSource by remember { mutableStateOf(savedLiveSource) }
    var epgSource by remember { mutableStateOf(savedEpgSource) }
    var isLoading by remember { mutableStateOf(false) }

    var lanUrl by remember { mutableStateOf<String?>(null) }
    var lanError by remember { mutableStateOf<String?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Focus/Editing states for TextFields
    var isLiveSourceEditing by remember { mutableStateOf(false) }
    var isEpgSourceEditing by remember { mutableStateOf(false) }

    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Log.d("MainActivity", "Starting update check...")
        val info = UpdateChecker.checkUpdate(context)
        Log.d("MainActivity", "Update check result: $info")
        if (info != null && info.hasUpdate) {
            Log.d("MainActivity", "Showing update dialog")
            updateInfo = info
            showUpdateDialog = true
        } else {
            Log.d("MainActivity", "No update available or check failed")
        }
    }

    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            onDismiss = { showUpdateDialog = false }
        )
    }

    fun loadAndPlay(url: String, save: Boolean) {
        if (url.isBlank()) {
            Toast.makeText(context, "请输入直播源地址", Toast.LENGTH_SHORT).show()
            return
        }
        
        isLoading = true
        if (save) {
            Prefs.setLiveSource(context, liveSource)
            Prefs.setEpgSource(context, epgSource)
        }

        coroutineScope.launch {
            try {
                if (save) Toast.makeText(context, "正在加载直播源...", Toast.LENGTH_SHORT).show()
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }
                
                if (response.isSuccessful) {
                    val content = withContext(Dispatchers.IO) {
                        response.body?.string()
                    }
                    
                    if (content != null) {
                        val playlistFileName = PlaylistCache.fileNameForSource(url)
                        withContext(Dispatchers.IO) {
                            PlaylistCache.write(context, playlistFileName, content)
                        }
                        Prefs.setPlaylistFileName(context, playlistFileName)

                        val channels = M3uParser.parse(content)
                        if (channels.isEmpty()) {
                            Toast.makeText(context, "未找到有效的直播频道", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val (lastUrl, lastTitle) = Prefs.getLastChannel(context)
                        val startIndex = findBestChannelIndex(channels, lastUrl, lastTitle) ?: 0
                        val startChannel = channels.getOrElse(startIndex) { channels.first() }

                        Prefs.setLastChannel(context, startChannel.url, startChannel.title)

                        val intent = Intent(context, PlayerActivity::class.java).apply {
                            putExtra("VIDEO_URL", startChannel.url)
                            putExtra("VIDEO_TITLE", startChannel.title)
                            putExtra("PLAYLIST_FILE_NAME", playlistFileName)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)

                        (context as? android.app.Activity)?.finish()
                    }
                } else {
                    Toast.makeText(context, "加载失败: ${response.code}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(context, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "发生错误: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    val onPush: (String, String) -> Unit = { live, epg ->
        mainHandler.post {
            if (live.isNotBlank()) liveSource = live
            if (epg.isNotBlank()) epgSource = epg
            Toast.makeText(context, "已从手机推送更新", Toast.LENGTH_SHORT).show()
            
            // Automatically save and play after push
            // Use the pushed live source if available, otherwise use the existing state (which was just updated if not blank)
            // But if pushed live is blank, we use current liveSource.
            // Note: if live is not blank, liveSource is already updated above.
            loadAndPlay(liveSource, true)
        }
    }

    DisposableEffect(Unit) {
        val server = SourceConfigLanServer(
            appContext = context.applicationContext,
            onPush = onPush,
        )
        try {
            val info = server.start()
            lanUrl = info.url
            lanError = null
        } catch (e: Exception) {
            lanUrl = null
            lanError = e.message ?: "局域网服务启动失败"
        }
        onDispose { server.stop() }
    }

    LaunchedEffect(lanUrl) {
        val url = lanUrl
        qrBitmap = if (url.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.Default) {
                runCatching { QrCode.toBitmap(url, 640) }.getOrNull()
            }
        }
    }

    // Auto-play removed as SplashActivity handles routing
    // LaunchedEffect(Unit) { ... }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        Text(
            text = "源配置",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.TopStart)
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.6f)
                    .aspectRatio(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    lanError != null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "局域网服务启动失败",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = lanError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    qrBitmap != null && !lanUrl.isNullOrBlank() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .aspectRatio(1f)
                                    .background(Color.White, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Image(
                                    bitmap = qrBitmap!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = lanUrl!!,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    else -> {
                        Text(
                            text = "正在生成二维码",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(48.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val textFieldColors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF303030),
                    unfocusedContainerColor = Color(0xFF303030),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color.LightGray,
                    unfocusedLabelColor = Color.Gray,
                    cursorColor = Color.White
                )

                TextField(
                    value = liveSource,
                    onValueChange = { liveSource = it },
                    label = { Text("直播源地址") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { 
                            if (!it.isFocused) isLiveSourceEditing = false 
                        }
                        .onKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                                    if (!isLiveSourceEditing) {
                                        isLiveSourceEditing = true
                                        keyboardController?.show()
                                    }
                                    false
                                } else false
                            } else false
                        },
                    colors = textFieldColors,
                    enabled = !isLoading,
                    readOnly = false
                )

                TextField(
                    value = epgSource,
                    onValueChange = { epgSource = it },
                    label = { Text("EPG地址") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { 
                            if (!it.isFocused) isEpgSourceEditing = false 
                        }
                        .onKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                                    if (!isEpgSourceEditing) {
                                        isEpgSourceEditing = true
                                        keyboardController?.show()
                                    }
                                    false
                                } else false
                            } else false
                        },
                    colors = textFieldColors,
                    enabled = !isLoading,
                    readOnly = false
                )

                Button(
                    onClick = { loadAndPlay(liveSource, true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (isLoading) "加载中..." else "保存并播放")
                    }
                }
            }
        }
    }
}

fun findBestChannelIndex(channels: List<Channel>, lastUrl: String?, lastTitle: String?): Int? {
    if (channels.isEmpty()) return null
    if (!lastUrl.isNullOrBlank()) {
        val urlIndex = channels.indexOfFirst { it.url == lastUrl }
        if (urlIndex >= 0) return urlIndex
    }
    if (!lastTitle.isNullOrBlank()) {
        val titleIndex = channels.indexOfFirst { it.title == lastTitle }
        if (titleIndex >= 0) return titleIndex
    }
    return 0
}

fun findChannel(m3uContent: String, lastUrl: String?, lastTitle: String?): Pair<String, String>? {
    val lines = m3uContent.lines()
    var currentTitle: String? = null
    
    var firstChannel: Pair<String, String>? = null
    var titleMatch: Pair<String, String>? = null
    
    for (line in lines) {
        val trimmedLine = line.trim()
        if (trimmedLine.startsWith("#EXTINF:")) {
            // Extract title
            val parts = trimmedLine.split(",")
            if (parts.size > 1) {
                currentTitle = parts.last().trim()
            }
        } else if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
            val url = trimmedLine
            val title = currentTitle ?: "Unknown"
            val channel = Pair(url, title)
            
            // First valid channel
            if (firstChannel == null) firstChannel = channel
            
            // Check for exact URL match
            if (lastUrl != null && url == lastUrl) {
                return channel
            }
            
            // Check for Title match (fallback if URL changed)
            if (lastTitle != null && title == lastTitle) {
                titleMatch = channel
            }
            
            currentTitle = null
        }
    }
    
    // Return priority: Title Match -> First Channel
    return titleMatch ?: firstChannel
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDialog(
    updateInfo: UpdateChecker.UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val confirmButtonFocusRequester = remember { FocusRequester() }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadComplete by remember { mutableStateOf(false) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        confirmButtonFocusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray,
        title = {
            M3Text(if (isDownloading) "正在下载..." else "发现新版本")
        },
        text = {
            if (isDownloading) {
                Column {
                    M3Text("下载进度: ${(downloadProgress * 100).toInt()}%")
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        trackColor = Color(0xFF424242),
                    )
                }
            } else {
                M3Text("当前版本: ${updateInfo.currentVersion}\n最新版本: ${updateInfo.latestTag}\n\n是否下载更新?")
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(
                    onClick = {
                        isDownloading = true
                        scope.launch {
                            val file = ApkDownloader.downloadWithProgress(
                                context = context,
                                url = updateInfo.downloadUrl
                            ) { progress ->
                                downloadProgress = progress.progress
                            }

                            if (file != null) {
                                downloadedFile = file
                                downloadComplete = true
                                ApkDownloader.installApk(context, file)
                                onDismiss()
                            } else {
                                isDownloading = false
                                Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.focusRequester(confirmButtonFocusRequester)
                ) {
                    Text("更新")
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}
