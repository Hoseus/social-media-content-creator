package com.hoseus.media

import java.nio.file.Path

interface FfmpegCommandBuilder {

    fun buildCreateTmpVideoCommand(audioFile: Path, subtitleFile: Path, resultFile: Path): List<String>

    fun buildCreateFinalVideoCommand(originalVideoFile: Path, newVideoFile: Path, takeNewVideoFromSeconds: Int, resultFile: Path): List<String>

    fun buildGetDurationCommand(file: Path): List<String>

    fun buildConcatFilesCommand(filesDir: Path, concatFile: Path, resultFile: Path, interleaveFile: Path? = null): List<String>
}