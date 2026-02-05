package com.cjlhll.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme

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
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: QR Code Placeholder
        Box(
            modifier = Modifier
                .fillMaxHeight(0.6f)
                .aspectRatio(1f)
                .border(2.dp, Color.Gray, RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "二维码",
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(48.dp))

        // Right side: Inputs and Button
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var liveSource by remember { mutableStateOf("") }
            var epgSource by remember { mutableStateOf("") }

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
                label = { Text("请输入直播源") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors
            )

            TextField(
                value = epgSource,
                onValueChange = { epgSource = it },
                label = { Text("请输入EPG源") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors
            )

            Button(
                onClick = { /* Save action */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("保存")
                }
            }
        }
    }
}
