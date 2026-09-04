package com.example.litetube.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

object YouTubeResolver {

    private fun resolutionNumber(label: String): Int {
        return Regex("""(\d{3,4})p""")
            .find(label)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }

    private fun isProbablyShortsUrl(url: String): Boolean {
        return url.contains("/shorts/", ignoreCase = true)
    }

    private fun audioScore(stream: AudioStream): Int {
        var score = stream.averageBitrate.coerceAtLeast(0)
        if (stream.format == MediaFormat.M4A) score += 1_000_000
        val codec = stream.codec.orEmpty().lowercase()
        if (codec.contains("mp4a") || codec.contains("aac")) score += 500_000
        return score
    }

    private fun videoScore(stream: VideoStream): Int {
        var score = 0
        val codec = stream.codec.orEmpty().lowercase()

        // Galaxy A7 2018: ưu tiên AVC/H.264 trước VP9/AV1 để giảm tải giải mã.
        if (codec.contains("avc") || codec.contains("h264")) score += 3_000_000
        if (stream.format == MediaFormat.MPEG_4) score += 2_000_000
        if (!stream.isVideoOnly) score += 500_000
        score += stream.fps.coerceAtMost(60) * 100
        return score
    }

    suspend fun resolve(
        watchUrl: String,
        requestedQuality: Int
    ): PlaybackSpec = withContext(Dispatchers.IO) {

        require(!isProbablyShortsUrl(watchUrl)) {
            "Shorts đã bị chặn theo cấu hình của ứng dụng."
        }

        val extractor = ServiceList.YouTube.getStreamExtractor(watchUrl)
        extractor.fetchPage()

        val audio = extractor.audioStreams
            .filter { it.isUrl && it.content.isNotBlank() }
            .maxByOrNull(::audioScore)
            ?: error("Không lấy được luồng âm thanh.")

        val allVideos = (extractor.videoStreams + extractor.videoOnlyStreams)
            .filter { it.isUrl && it.content.isNotBlank() }
            .filter { resolutionNumber(it.resolution) in 144..1080 }

        if (allVideos.isEmpty()) error("Không lấy được luồng video phù hợp.")

        val availableHeights = allVideos
            .map { resolutionNumber(it.resolution) }
            .filter { it > 0 }
            .distinct()
            .sorted()

        val targetHeight = when {
            requestedQuality <= 0 -> {
                // Auto cho A7: ưu tiên 720p nếu có, nếu không lấy mức gần nhất thấp hơn;
                // người dùng vẫn có thể chọn 1080p thủ công.
                availableHeights.filter { it <= 720 }.maxOrNull()
                    ?: availableHeights.minOrNull()
                    ?: 360
            }
            requestedQuality in availableHeights -> requestedQuality
            else -> availableHeights.filter { it <= requestedQuality }.maxOrNull()
                ?: availableHeights.minOrNull()
                ?: requestedQuality
        }

        val chosenVideo = allVideos
            .filter { resolutionNumber(it.resolution) == targetHeight }
            .maxByOrNull(::videoScore)
            ?: allVideos.minByOrNull {
                kotlin.math.abs(resolutionNumber(it.resolution) - targetHeight)
            }
            ?: error("Không chọn được luồng video.")

        PlaybackSpec(
            title = extractor.name,
            watchUrl = watchUrl,
            videoUrl = chosenVideo.content,
            audioUrl = audio.content,
            videoContainsAudio = !chosenVideo.isVideoOnly,
            selectedQuality = chosenVideo.resolution
        )
    }
}
