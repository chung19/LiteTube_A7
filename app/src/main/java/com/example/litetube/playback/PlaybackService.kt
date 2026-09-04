package com.example.litetube.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_PLAY_RESOLVED = "com.example.litetube.PLAY_RESOLVED"
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_AUDIO_URL = "audio_url"
        const val EXTRA_VIDEO_HAS_AUDIO = "video_has_audio"
        const val EXTRA_TITLE = "title"
    }

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory

    private var currentSpec: Spec? = null
    private var screenOffAudioOnly = false

    private data class Spec(
        val title: String,
        val videoUrl: String,
        val audioUrl: String,
        val videoHasAudio: Boolean
    )

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (currentSpec != null && !screenOffAudioOnly) {
                        switchMode(audioOnly = true)
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (currentSpec != null && screenOffAudioOnly) {
                        switchMode(audioOnly = false)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("LiteTubeA7/0.1")
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PLAY_RESOLVED) {
            val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL).orEmpty()
            val audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL).orEmpty()
            if (videoUrl.isNotBlank() && audioUrl.isNotBlank()) {
                currentSpec = Spec(
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    videoUrl = videoUrl,
                    audioUrl = audioUrl,
                    videoHasAudio = intent.getBooleanExtra(EXTRA_VIDEO_HAS_AUDIO, false)
                )
                screenOffAudioOnly = false
                startCurrentAt(0L, playWhenReady = true, audioOnly = false)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun source(url: String): MediaSource {
        return mediaSourceFactory.createMediaSource(MediaItem.fromUri(url))
    }

    private fun startCurrentAt(positionMs: Long, playWhenReady: Boolean, audioOnly: Boolean) {
        val spec = currentSpec ?: return

        val selectedSource = if (audioOnly) {
            source(spec.audioUrl)
        } else if (spec.videoHasAudio) {
            source(spec.videoUrl)
        } else {
            MergingMediaSource(
                source(spec.videoUrl),
                source(spec.audioUrl)
            )
        }

        player.setMediaSource(selectedSource)
        player.prepare()
        player.seekTo(positionMs.coerceAtLeast(0L))
        player.playWhenReady = playWhenReady
    }

    private fun switchMode(audioOnly: Boolean) {
        val spec = currentSpec ?: return
        val pos = player.currentPosition
        val shouldPlay = player.playWhenReady
        screenOffAudioOnly = audioOnly
        startCurrentAt(pos, shouldPlay, audioOnly)
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Không dừng nếu người dùng vuốt app khỏi Recent trong lúc đang phát.
        if (!player.isPlaying) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        mediaSession.release()
        player.release()
        super.onDestroy()
    }
}
