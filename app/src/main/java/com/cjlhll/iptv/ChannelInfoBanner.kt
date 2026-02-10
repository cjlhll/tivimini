package com.cjlhll.iptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Format
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun ChannelInfoBanner(
    visible: Boolean,
    channel: Channel?,
    programTitle: String?,
    channelNumber: Int = -1,
    programProgress: Float? = null,
    videoFormat: Format?,
    roundedTopCorners: Boolean = true,
    animate: Boolean = true,
    modifier: Modifier = Modifier
) {
    // UI风格和抽屉保持一致
    // 抽屉是 surface alpha 0.92f
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    // 底部浮窗，上面两个角圆角
    val shape = if (roundedTopCorners) RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp) else RectangleShape

    val content: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor, shape)
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ChannelLogo(
                    logoUrl = channel?.logoUrl,
                    fallbackTitle = channel?.title ?: "",
                    modifier = Modifier.size(width = 80.dp, height = 50.dp),
                    width = 80.dp,
                    height = 50.dp
                )

                Spacer(modifier = Modifier.width(20.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val titleText = if (channelNumber > 0) {
                        "$channelNumber. ${channel?.title ?: "未知频道"}"
                    } else {
                        channel?.title ?: "未知频道"
                    }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!programTitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = programTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (programProgress != null) {
                        BannerProgressBar(progress = programProgress)
                    }
                }
            }

                Spacer(modifier = Modifier.width(20.dp))

                Column(horizontalAlignment = Alignment.End) {
                    val width = videoFormat?.width ?: 0
                    val height = videoFormat?.height ?: 0
                    val bitrate = videoFormat?.bitrate ?: -1
                    val channels = videoFormat?.channelCount ?: -1
                    val mime = videoFormat?.sampleMimeType ?: ""

                    val resolutionText = if (width > 0 && height > 0) "${width}x${height}" else ""
                    val bitrateText = if (bitrate > 0) "${bitrate / 1000} kbps" else ""
                    val audioText = if (channels > 0) "${channels}ch" else ""
                    val codecText = mime.substringAfter("/")

                    if (resolutionText.isNotEmpty()) TechSpecText(resolutionText)
                    if (bitrateText.isNotEmpty()) TechSpecText(bitrateText)

                    Row {
                        if (codecText.isNotEmpty()) {
                            TechSpecText(codecText)
                            if (audioText.isNotEmpty()) {
                                Text(
                                    text = " | ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                        if (audioText.isNotEmpty()) TechSpecText(audioText)
                    }
                }
            }
        }
    }

    if (animate) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = modifier.fillMaxWidth()
        ) {
            content()
        }
    } else {
        if (!visible) return
        Box(modifier = modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun TechSpecText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
}

@Composable
private fun BannerProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val clamped = progress.coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.80f)
    Box(
        modifier = modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .height(3.dp)
            .background(track, RoundedCornerShape(999.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped)
                .background(fill, RoundedCornerShape(999.dp))
        )
    }
}
