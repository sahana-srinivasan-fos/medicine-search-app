package com.rxora.app.voice.whisper

object WhisperCpuConfig {
    val preferredThreadCount: Int
        get() = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
}
