package com.hoseus.tts

import io.smallrye.mutiny.Uni
import java.nio.file.Path

interface TtsService {

    fun batchTts(texts: List<String>, speakerSex: SpeakerSex, resultFileProvider: (Int, String, SpeakerSex) -> Path): Uni<List<Path>>

    fun tts(text: String, speakerSex: SpeakerSex, resultFile: Path): Uni<Path>
}