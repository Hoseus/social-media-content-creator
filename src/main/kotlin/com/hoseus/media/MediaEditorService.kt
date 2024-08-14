package com.hoseus.media

import io.smallrye.mutiny.Uni
import java.nio.file.Path
import kotlin.time.Duration

interface MediaEditorService {
    fun batchCreateTmpVideo(audioFiles: List<Path>, subtitleFiles: List<Path>, resultFileProvider: (Int, Path, Path) -> Path): Uni<List<Path>>

    fun createTmpVideo(audioFile: Path, subtitleFile: Path, resultFile: Path): Uni<Path>

    fun createFinalVideo(originalVideoFile: Path, newVideoFile: Path, resultFile: Path): Uni<Path>

    fun getDuration(file: Path): Uni<Duration>

    fun concatFiles(files: List<Path>, resultFile: Path, interleaveFile: Path? = null): Uni<Path>
}