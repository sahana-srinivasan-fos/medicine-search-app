package com.rxora.app.voice

import java.io.File

interface VoiceTranscriber {
    suspend fun transcribe(audioFile: File): String
}
