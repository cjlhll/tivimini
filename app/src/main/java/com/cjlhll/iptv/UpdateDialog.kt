package com.cjlhll.iptv

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text as M3Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdateDialog(
    updateInfo: UpdateChecker.UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val confirmButtonFocusRequester = remember { FocusRequester() }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        confirmButtonFocusRequester.requestFocus()
    }

    val dialogContainerColor = Color(0xFF1E1E1E)

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        containerColor = dialogContainerColor,
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
