package com.example.litetube

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.example.litetube.playback.PlaybackService
import com.example.litetube.youtube.YouTubeResolver
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var urlInput: EditText
    private lateinit var qualitySpinner: Spinner
    private lateinit var statusText: TextView
    private lateinit var playButton: Button

    private var controllerFuture:
        com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    private val qualityLabels = listOf(
        "Tự động (ưu tiên 720p)",
        "360p",
        "480p",
        "720p",
        "1080p"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        urlInput = findViewById(R.id.urlInput)
        qualitySpinner = findViewById(R.id.qualitySpinner)
        statusText = findViewById(R.id.statusText)
        playButton = findViewById(R.id.playButton)

        qualitySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            qualityLabels
        )
        qualitySpinner.setSelection(3) // 720p mặc định; 1080p vẫn chọn được.

        connectController()
        acceptSharedUrl(intent)

        playButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isBlank()) {
                statusText.text = "Chưa có URL."
                return@setOnClickListener
            }

            val requested = when (qualitySpinner.selectedItemPosition) {
                1 -> 360
                2 -> 480
                3 -> 720
                4 -> 1080
                else -> 0
            }

            playButton.isEnabled = false
            statusText.text = "Đang lấy luồng phát…"

            lifecycleScope.launch {
                try {
                    val spec = YouTubeResolver.resolve(url, requested)

                    val playIntent = Intent(
                        this@MainActivity,
                        PlaybackService::class.java
                    ).apply {
                        action = PlaybackService.ACTION_PLAY_RESOLVED
                        putExtra(PlaybackService.EXTRA_TITLE, spec.title)
                        putExtra(PlaybackService.EXTRA_VIDEO_URL, spec.videoUrl)
                        putExtra(PlaybackService.EXTRA_AUDIO_URL, spec.audioUrl)
                        putExtra(
                            PlaybackService.EXTRA_VIDEO_HAS_AUDIO,
                            spec.videoContainsAudio
                        )
                    }

                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        playIntent
                    )

                    statusText.text =
                        "${spec.title}\nĐang phát: ${spec.selectedQuality}. " +
                        "Tắt màn hình sẽ chuyển sang audio-only."
                } catch (e: Exception) {
                    statusText.text = "Lỗi: ${e.message ?: e.javaClass.simpleName}"
                } finally {
                    playButton.isEnabled = true
                }
            }
        }
    }

    private fun connectController() {
        val token = SessionToken(
            this,
            android.content.ComponentName(
                this,
                PlaybackService::class.java
            )
        )

        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener(
            {
                try {
                    val controller = controllerFuture?.get()
                    playerView.player = controller
                } catch (_: Exception) {
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun acceptSharedUrl(sourceIntent: Intent?) {
        if (sourceIntent?.action == Intent.ACTION_SEND &&
            sourceIntent.type == "text/plain"
        ) {
            val shared = sourceIntent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val url = Regex("""https?://\S+""").find(shared)?.value
            if (!url.isNullOrBlank()) urlInput.setText(url)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptSharedUrl(intent)
    }

    override fun onDestroy() {
        playerView.player = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }
}
