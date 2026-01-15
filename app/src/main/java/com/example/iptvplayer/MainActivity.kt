package com.example.iptvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.iptvplayer.ui.screens.HomeScreen
import com.example.iptvplayer.ui.screens.PlayerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            IPTVPlayerTheme {
                // Keep 'rememberSaveable'
                var currentScreen by rememberSaveable { mutableStateOf("home") }
                var selectedChannelUrl by rememberSaveable { mutableStateOf("") }
                var selectedChannelName by rememberSaveable { mutableStateOf("Live TV") }

                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HomeScreen(
                            onChannelClick = { channel ->
                                selectedChannelUrl = channel.url
                                selectedChannelName = channel.name
                                currentScreen = "player"
                            }
                        )

                        AnimatedVisibility(
                            visible = currentScreen == "player",
                            enter = slideInVertically(initialOffsetY = { it }), // Slide up animation
                            exit = slideOutVertically(targetOffsetY = { it })
                        ) {
                            androidx.activity.compose.BackHandler {
                                currentScreen = "home"
                            }

                            PlayerScreen(
                                videoUrl = selectedChannelUrl,
                                channelName = selectedChannelName,
                                onBack = { currentScreen = "home" }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IPTVPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        darkTheme -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}