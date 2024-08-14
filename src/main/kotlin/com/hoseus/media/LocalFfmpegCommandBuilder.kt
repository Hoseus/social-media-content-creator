package com.hoseus.media

import io.quarkus.arc.profile.IfBuildProfile
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@ApplicationScoped
@IfBuildProfile(value = "prod")
class LocalFfmpegCommandBuilder : FfmpegCommandBuilder {

    override fun buildCreateTmpVideoCommand(audioFile: Path, subtitleFile: Path, resultFile: Path): List<String> {
        return listOfNotNull(
            "ffmpeg",
            "-i", audioFile.absolutePathString(),
            "-i", subtitleFile.absolutePathString(),
            "-map", "0:a",
            "-map", "1:s",
            "-c:a", "copy",
            "-c:s", "mov_text",
            "-y",
            resultFile.absolutePathString()
        )
    }

    override fun buildCreateFinalVideoCommand(originalVideoFile: Path, newVideoFile: Path, takeNewVideoFromSeconds: Int, resultFile: Path): List<String> {
        return listOfNotNull(
            "ffmpeg",
            "-ss", takeNewVideoFromSeconds.toString(),
            "-i", newVideoFile.absolutePathString(),
            "-i", originalVideoFile.absolutePathString(),
            "-map", "0:v",
            "-map", "1:a",
            "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-movflags", "+faststart",
            "-c:a", "aac", "-b:a", "192k", "-ac", "2",
            "-vf", "subtitles=${originalVideoFile.absolutePathString()}:force_style=FontName='Lobster Two,Fontsize=28,Alignment=2,MarginV=20,PrimaryColour=&H0000A5FF'",
            "-shortest",
            "-y",
            resultFile.absolutePathString()
        )
    }

    override fun buildGetDurationCommand(file: Path): List<String> {
        return listOfNotNull(
            "ffprobe",
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            file.absolutePathString()
        )
    }

    override fun buildConcatFilesCommand(filesDir: Path, concatFile: Path, resultFile: Path, interleaveFile: Path?): List<String> {
        return listOfNotNull(
            "ffmpeg",
            "-f", "concat",
            "-safe", "0",
            "-i", concatFile.absolutePathString(),
            "-c:v", "copy",
            "-c:a", "copy",
            "-c:s", "mov_text",
            "-y",
            resultFile.absolutePathString()
        )
    }

    companion object LocalFfmpegCommandBuilder {
        private val logger = Logger.getLogger(LocalFfmpegCommandBuilder::class.java.name)
    }
}