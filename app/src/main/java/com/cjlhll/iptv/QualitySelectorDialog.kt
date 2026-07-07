package com.cjlhll.iptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

data class QualityVariantUi(
    val label: String,
    val selected: Boolean
)

@Composable
fun QualitySelectorDialog(
    visible: Boolean,
    channelTitle: String,
    variants: List<QualityVariantUi>,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
    val shape = RoundedCornerShape(18.dp)
    val scrollState = rememberScrollState()
    val itemRequesters = remember(variants.size) {
        List(variants.size) { FocusRequester() }
    }

    LaunchedEffect(visible, variants) {
        if (visible && variants.isNotEmpty()) {
            val selectedIndex = variants.indexOfFirst { it.selected }.coerceAtLeast(0)
            itemRequesters.getOrNull(selectedIndex)?.requestFocus()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.96f),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.96f),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .onPreviewKeyEvent {
                    if (it.key == Key.Back) {
                        onClose()
                        true
                    } else {
                        false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .heightIn(max = 480.dp)
                    .background(container, shape)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "清晰度选择",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (channelTitle.isNotBlank()) {
                        Spacer(modifier = Modifier.padding(top = 4.dp))
                        Text(
                            text = channelTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.padding(top = 16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                    ) {
                        variants.forEachIndexed { index, variant ->
                            QualityOptionItem(
                                text = if (variant.selected) "${variant.label} (当前)" else variant.label,
                                focusRequester = itemRequesters.getOrNull(index),
                                onClick = { onSelect(index) }
                            )
                            Spacer(modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityOptionItem(
    text: String,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.01f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { m -> if (focusRequester != null) m.focusRequester(focusRequester) else m }
            .background(bg, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable {
                onClick()
                focusRequester?.requestFocus()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
