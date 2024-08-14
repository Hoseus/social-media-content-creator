package com.hoseus.media

import io.quarkus.arc.profile.IfBuildProfile
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedReader
import kotlin.io.path.name
import kotlin.io.path.writeLines

@ApplicationScoped
@IfBuildProfile(anyOf = ["dev", "test"])
class DockerizedFfmpegCommandBuilder : FfmpegCommandBuilder {
    private val containerInputDir = Paths.get("/input")
    private val containerOutputDir = Paths.get("/output")
    private val containerTmpDir = Paths.get("/tmp")

    override fun buildCreateTmpVideoCommand(audioFile: Path, subtitleFile: Path, resultFile: Path): List<String> {
        val containerAudioFile = Paths.get(this.containerInputDir.absolutePathString(), audioFile.name)
        val containerSubtitleFile = Paths.get(this.containerInputDir.absolutePathString(), subtitleFile.name)
        val containerResultFile = Paths.get(this.containerOutputDir.absolutePathString(), resultFile.name)

        return listOfNotNull(
            *this.buildDockerRun(
                "-v", "${audioFile.absolutePathString()}:${containerAudioFile.absolutePathString()}",
                "-v", "${subtitleFile.absolutePathString()}:${containerSubtitleFile.absolutePathString()}",
                "-v", "${resultFile.toAbsolutePath().parent.absolutePathString()}:${containerResultFile.toAbsolutePath().parent.absolutePathString()}",
            ),
            "-i", containerAudioFile.absolutePathString(),
            "-i", containerSubtitleFile.absolutePathString(),
            "-map", "0:a",
            "-map", "1:s",
            "-c:a", "copy",
            "-c:s", "mov_text",
            "-y",
            containerResultFile.absolutePathString()
        )
    }

    override fun buildCreateFinalVideoCommand(originalVideoFile: Path, newVideoFile: Path, takeNewVideoFromSeconds: Int, resultFile: Path): List<String> {
        val containerNewVideoFile = Paths.get(this.containerInputDir.absolutePathString(), newVideoFile.name)
        val containerOriginalVideoFile = Paths.get(this.containerInputDir.absolutePathString(), originalVideoFile.name)
        val containerResultFile = Paths.get(this.containerOutputDir.absolutePathString(), resultFile.name)

        return listOfNotNull(
            *this.buildDockerRun(
                "-v", "${originalVideoFile.absolutePathString()}:${containerOriginalVideoFile.absolutePathString()}",
                "-v", "${newVideoFile.absolutePathString()}:${containerNewVideoFile.absolutePathString()}",
                "-v", "${resultFile.toAbsolutePath().parent.absolutePathString()}:${containerResultFile.toAbsolutePath().parent.absolutePathString()}",
            ),
            "-ss", takeNewVideoFromSeconds.toString(),
            "-i", containerNewVideoFile.absolutePathString(),
            "-i", containerOriginalVideoFile.absolutePathString(),
            "-map", "0:v",
            "-map", "1:a",
            "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-movflags", "+faststart",
            "-c:a", "aac", "-b:a", "192k", "-ac", "2",
            "-vf", "subtitles=${containerOriginalVideoFile.absolutePathString()}:force_style=FontName='Lobster Two,Fontsize=28,Alignment=2,MarginV=20,PrimaryColour=&H0000A5FF'",
            "-shortest",
            "-y",
            containerResultFile.absolutePathString()
        )
    }

    override fun buildGetDurationCommand(file: Path): List<String> {
        val containerFile = Paths.get(this.containerInputDir.absolutePathString(), file.name)

        return listOfNotNull(
            *this.buildDockerRun(
                "-v", "${file.absolutePathString()}:${containerFile.absolutePathString()}",
                "--entrypoint", "ffprobe"
            ),
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            containerFile.absolutePathString()
        )
    }

    override fun buildConcatFilesCommand(filesDir: Path, concatFile: Path, resultFile: Path, interleaveFile: Path?): List<String> {
        val containerConcatFile = Paths.get(this.containerTmpDir.absolutePathString(), concatFile.name)
        val containerFilesDir = Paths.get(this.containerInputDir.absolutePathString(), filesDir.name)
        val containerResultFile = Paths.get(this.containerOutputDir.absolutePathString(), resultFile.name)

        this.editConcatFile(concatFile, containerFilesDir)

        return listOfNotNull(
            *this.buildDockerRun(
                "-v", "${filesDir.absolutePathString()}:${containerFilesDir.absolutePathString()}",
                "-v", "${concatFile.absolutePathString()}:${containerConcatFile.absolutePathString()}",
                interleaveFile?.let {
                    val containerInterleaveFile = Paths.get(this.containerInputDir.absolutePathString(), interleaveFile.name)
                    "-v ${interleaveFile.absolutePathString()}:${containerInterleaveFile.absolutePathString()}"
                },
                "-v", "${resultFile.toAbsolutePath().parent.absolutePathString()}:${containerResultFile.toAbsolutePath().parent.absolutePathString()}",
            ),
            "-f", "concat",
            "-safe", "0",
            "-i", containerConcatFile.absolutePathString(),
            "-c:v", "copy",
            "-c:a", "copy",
            "-c:s", "mov_text",
            "-y",
            containerResultFile.absolutePathString()
        )
    }

    private fun editConcatFile(concatFile: Path, containerFilesDir: Path) {
        val concatFileModifiedLines = concatFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.toList().map { line ->
                line.replaceBeforeLast("/", "file $containerFilesDir")
            }
        }

        concatFile.writeLines(concatFileModifiedLines, Charsets.UTF_8)
    }

    private fun buildDockerRun(vararg configs: String?): Array<String?> {
        return arrayOf(
            "docker",
            "run",
            "--rm",
            *configs,
            "lscr.io/linuxserver/ffmpeg:latest"
        )
    }

    companion object DockerizedFfmpegCommandBuilder {
        private val logger = Logger.getLogger(DockerizedFfmpegCommandBuilder::class.java.name)
    }
}