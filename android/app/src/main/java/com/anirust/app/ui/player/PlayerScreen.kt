package com.anirust.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.anirust.app.domain.model.StreamMedia
import com.anirust.app.ui.components.ErrorView
import com.anirust.app.ui.components.LoadingView
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.anime?.displayTitle ?: "Просмотр",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Серия " +
                                state.episodeNumber +
                                (state.dubbing?.let { " · " + it } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.openInExternalPlayer(context) },
                        enabled = state.stream != null && !state.isLoading,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Открыть во внешнем плеере")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                state.isLoading -> LoadingView("Готовим серию " + state.episodeNumber + "…")
                state.error != null ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ErrorView(state.error!!, onRetry = viewModel::loadStream)
                        if (state.stream != null)
                            FilledTonalButton(
                                onClick = { viewModel.openInExternalPlayer(context) }
                            ) {
                                Text("Попробовать внешний плеер")
                            }
                    }
                state.stream != null ->
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        StreamPlayer(state.stream!!, viewModel)
                    }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun StreamPlayer(stream: StreamMedia, viewModel: PlayerViewModel) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember(stream) { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(stream, lifecycle) {
        fun save(current: ExoPlayer) {
            viewModel.saveHistoryProgress(
                current.currentPosition,
                current.duration,
                current.playWhenReady,
            )
        }
        fun release() {
            player?.let { current ->
                save(current)
                current.release()
            }
            player = null
        }
        fun prepare() {
            if (player != null) return
            val state = viewModel.uiState.value
            val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(stream.headers)
            val current =
                ExoPlayer.Builder(context)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(http))
                    .build()
            current.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            current.setHandleAudioBecomingNoisy(true)
            current.addListener(
                object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        viewModel.onPlaybackError()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            current.playWhenReady = false
                            viewModel.saveHistoryProgress(current.duration, current.duration, false)
                        }
                    }
                }
            )
            current.setMediaItem(MediaItem.fromUri(stream.streamUrl))
            current.seekTo(state.currentPositionMs)
            current.playWhenReady = state.isPlaying
            player = current
            current.prepare()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> prepare()
                Lifecycle.Event.ON_STOP -> release()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) prepare()
        onDispose {
            lifecycle.removeObserver(observer)
            release()
        }
    }

    LaunchedEffect(player) {
        val current = player ?: return@LaunchedEffect
        while (true) {
            delay(5000)
            viewModel.saveHistoryProgress(
                current.currentPosition,
                current.duration,
                current.playWhenReady,
            )
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setShowSubtitleButton(true)
            }
        },
        update = { view ->
            view.player = player
            view.keepScreenOn = player != null
        },
        onRelease = { view ->
            view.player = null
            view.keepScreenOn = false
        },
        modifier = Modifier.fillMaxSize(),
    )
}
