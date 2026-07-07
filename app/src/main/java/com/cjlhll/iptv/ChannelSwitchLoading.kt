package com.cjlhll.iptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Stable
class ChannelSwitchController {
    private val _visible = mutableStateOf(false)
    val visibleState: State<Boolean> = _visible

    private var sessionId by mutableIntStateOf(0)

    fun show() {
        sessionId++
        _visible.value = true
    }

    fun hide() {
        _visible.value = false
    }

    val currentSession: Int
        get() = sessionId

    val visible: Boolean
        get() = _visible.value
}

@Composable
fun rememberChannelSwitchController(): ChannelSwitchController {
    return remember { ChannelSwitchController() }
}

@Composable
fun BindChannelSwitchLoading(
    controller: ChannelSwitchController,
    player: Player?,
) {
    DisposableEffect(player, controller) {
        val p = player ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (!controller.visible) return
                if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                    controller.hide()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (controller.visible) {
                    controller.hide()
                }
            }
        }
        p.addListener(listener)
        onDispose { p.removeListener(listener) }
    }

    val session = controller.currentSession
    LaunchedEffect(session) {
        if (session > 0 && controller.visible) {
            kotlinx.coroutines.delay(12_000)
            controller.hide()
        }
    }
}

@Composable
fun ChannelSwitchOverlay(
    controller: ChannelSwitchController,
    modifier: Modifier = Modifier,
    message: String = "加载中...",
) {
    val visible by controller.visibleState
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
