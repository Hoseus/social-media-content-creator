package com.hoseus.media

import com.hoseus.cli.CommandLineExecutor
import com.hoseus.exception.CommandLineExecutionFailedException
import com.hoseus.exception.UnexpectedErrorException
import io.smallrye.mutiny.Uni
import io.vertx.mutiny.core.buffer.Buffer
import io.vertx.mutiny.core.file.FileSystem
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.function.Supplier
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@ApplicationScoped
class FfmpegMediaEditorService(
    @ConfigProperty(name = "app.media-editor.batch.size") private val batchSize: Int,
    @ConfigProperty(name = "app.media-editor.ffmpeg.tmp-dir") private val tmpDir: Path,
    private val fileSystem: FileSystem,
    private val ffmpegCommandBuilder: FfmpegCommandBuilder,
    private val commandLineExecutor: CommandLineExecutor
) : MediaEditorService {
    override fun batchCreateTmpVideo(audioFiles: List<Path>, subtitleFiles: List<Path>, resultFileProvider: (Int, Path, Path) -> Path): Uni<List<Path>> {
        return this.batchExecute {
            audioFiles.zip(subtitleFiles).mapIndexed { i, (audioFile, subtitlesFile) ->
                this.createTmpVideo(audioFile, subtitlesFile, resultFileProvider.invoke(i, audioFile, subtitlesFile))
            }
        }
    }

    override fun createTmpVideo(audioFile: Path, subtitleFile: Path, resultFile: Path): Uni<Path> {

        val command = this.ffmpegCommandBuilder.buildCreateTmpVideoCommand(
            audioFile = audioFile,
            subtitleFile = subtitleFile,
            resultFile = resultFile
        )

        return this.commandLineExecutor.executeCommand(command = command)
            .map { resultFile }
            .onFailure().invoke { e ->
                when(e) {
                    is CommandLineExecutionFailedException -> logger.error("Ffmpeg failed creating tmp video - exit value: ${e.exitValue} - command: ${e.command} - audio file: ${audioFile.absolutePathString()} - result file: ${resultFile.absolutePathString()}", e)
                    else -> logger.error("Error creating tmp video - audio file: ${audioFile.absolutePathString()} - result file: ${resultFile.absolutePathString()}", e)
                }
            }
            .onFailure().transform { e -> UnexpectedErrorException(message = "Error creating tmp video", cause = e) }
    }

    override fun createFinalVideo(originalVideoFile: Path, newVideoFile: Path, resultFile: Path): Uni<Path> {
        return Uni
            .combine()
            .all()
            .unis(
                this.getDuration(file = originalVideoFile),
                this.getDuration(file = newVideoFile)
            )
            .asTuple()
            .map { durations ->
                val originalVideoDuration = durations.item1
                val newVideoDuration = durations.item2
                (newVideoDuration - originalVideoDuration).toInt(DurationUnit.SECONDS)
            }.flatMap { outputVideoMaxStartSeconds ->
                val command = this.ffmpegCommandBuilder.buildCreateFinalVideoCommand(
                    originalVideoFile = originalVideoFile,
                    newVideoFile = newVideoFile,
                    takeNewVideoFromSeconds = (0..outputVideoMaxStartSeconds).random(),
                    resultFile = resultFile
                )

                this.commandLineExecutor.executeCommand(command = command)
            }.map { resultFile }
            .onFailure().invoke { e ->
                when(e) {
                    is CommandLineExecutionFailedException -> logger.error("Ffmpeg failed creating final video - exit value: ${e.exitValue} - command: ${e.command} - original video file: ${originalVideoFile.absolutePathString()} - new video file: ${newVideoFile.absolutePathString()} - result file: ${resultFile.absolutePathString()}", e)
                    else -> logger.error("Error creating final video - original video file: ${originalVideoFile.absolutePathString()} - new video file: ${newVideoFile.absolutePathString()} - result file: ${resultFile.absolutePathString()}", e)
                }
            }
            .onFailure().transform { e -> UnexpectedErrorException(message = "Error creating final video", cause = e) }
    }

    override fun getDuration(file: Path): Uni<Duration> {
        val command = this.ffmpegCommandBuilder.buildGetDurationCommand(file = file)

        return this.commandLineExecutor.executeCommand(command = command)
            .map { it.toDouble().toDuration(DurationUnit.SECONDS) }
            .onFailure().invoke { e ->
                when(e) {
                    is CommandLineExecutionFailedException -> logger.error("Ffmpeg failed getting duration of file - exit value: ${e.exitValue} - command: ${e.command} - file: ${file.absolutePathString()}", e)
                    else -> logger.error("Error getting duration of file - file: ${file.absolutePathString()}", e)
                }
            }
            .onFailure().transform { e -> UnexpectedErrorException(message = "Error getting duration of file", cause = e) }
    }

    override fun concatFiles(files: List<Path>, resultFile: Path, interleaveFile: Path?): Uni<Path> {
        if(files.distinctBy { it.toAbsolutePath().parent }.size > 1) {
            return Uni.createFrom().failure(IllegalArgumentException("All files to concat must be in the same directory"))
        }

        return this.writeConcatFile(files, interleaveFile)
            .flatMap { concatFile ->
                val command = this.ffmpegCommandBuilder.buildConcatFilesCommand(
                    filesDir = files.first().toAbsolutePath().parent,
                    concatFile = concatFile,
                    resultFile = resultFile,
                    interleaveFile = interleaveFile
                )

                this.commandLineExecutor.executeCommand(command)
            }.map { resultFile }
            .onFailure().invoke { e ->
                when(e) {
                    is CommandLineExecutionFailedException -> logger.error("Ffmpeg failed concatenating files - exit value: ${e.exitValue} - command: ${e.command} - files: ${files.map { it.absolutePathString() }} - result file: ${resultFile.absolutePathString()} - interleave file: ${interleaveFile?.absolutePathString()}", e)
                    else -> logger.error("Error concatenating files - files: ${files.map { it.absolutePathString() }} - result file: ${resultFile.absolutePathString()} - interleave file: ${interleaveFile?.absolutePathString()}", e)
                }
            }
            .onFailure().transform { e -> UnexpectedErrorException(message = "Error concatenating files", cause = e) }
    }

    private fun writeConcatFile(files: List<Path>, interleaveFile: Path?): Uni<Path> {
        val filesDir = files.first().toAbsolutePath().parent
        val concatFileContentSeparator = interleaveFile?.let {
            "\nfile '${it.absolutePathString()}'\n"
        } ?: "\n"

        val concatFile = Paths.get(this.tmpDir.absolutePathString(), "${UUID.randomUUID()}_concat_list.txt")
        val concatFileContent = files.joinToString(separator = concatFileContentSeparator, transform = { "file '${filesDir.absolutePathString() + "/" + it.name}'" })

        return this.fileSystem.writeFile(
            concatFile.absolutePathString(), Buffer.buffer(concatFileContent)
        ).map {
            concatFile
        }.onFailure().invoke{ e -> logger.error("Error writing concat file - ${concatFile.absolutePathString()}", e) }
    }

    private fun <T> batchExecute(toExecute: Supplier<List<Uni<T>>>): Uni<List<T>> {
        return Uni.join().all(
            toExecute.get()
        ).usingConcurrencyOf(this.batchSize).andFailFast()
    }

    companion object FfmpegMediaEditorService {
        private val logger = Logger.getLogger(FfmpegMediaEditorService::class.java.name)
    }
}