package com.hoseus.subtitle

import io.smallrye.mutiny.Uni
import java.nio.file.Path

interface SubtitlesService {
    fun generateSubtitles(audioFile: Path, transcriptFile: Path, resultFile: Path): Uni<Path>
    fun batchGenerateSubtitles(audioFiles: List<Path>, transcriptFiles: List<Path>, resultFileProvider: (Int, Path, Path) -> Path): Uni<List<Path>>
}