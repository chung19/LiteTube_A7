package com.example.litetube.youtube

data class PlaybackSpec(
    val title: String,
    val watchUrl: String,
    val videoUrl: String,
    val audioUrl: String,
    val videoContainsAudio: Boolean,
    val selectedQuality: String
)
