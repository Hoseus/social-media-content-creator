package com.hoseus.tts

import java.nio.file.Path

interface CoquiTtsCommandBuilder {

    fun buildSynthesizeSpeechCommand(text: String, speakerSex: SpeakerSex, resultFile: Path): List<String>
}