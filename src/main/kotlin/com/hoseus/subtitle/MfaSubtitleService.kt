package com.hoseus.subtitle

import com.hoseus.cli.CommandLineExecutor
import com.hoseus.exception.CommandLineExecutionFailedException
import com.hoseus.exception.UnexpectedErrorException
import io.quarkus.arc.properties.IfBuildProperty
import io.smallrye.mutiny.Uni
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.mutiny.core.buffer.Buffer
import io.vertx.mutiny.core.file.FileSystem
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import kotlin.io.path.absolutePathString

@ApplicationScoped
@IfBuildProperty(name = "app.subtitle.implementation", stringValue = "mfa", enableIfMissing = false)
class MfaSubtitleService(
    @ConfigProperty(name = "app.subtitle.batch.size") private val batchSize: Int,
    @ConfigProperty(name = "app.subtitle.mfa.tmp-dir") private val tmpDir: Path,
    private val fileSystem: FileSystem,
    private val mfaCommandBuilder: MfaCommandBuilder,
    private val commandLineExecutor: CommandLineExecutor
) : SubtitlesService {

    override fun generateSubtitles(audioFile: Path, transcriptFile: Path, resultFile: Path): Uni<Path> {
        val tmpDir = Paths.get(this.tmpDir.absolutePathString(), UUID.randomUUID().toString())

        return this.fileSystem.mkdir(tmpDir.absolutePathString()).flatMap {
            this.tokenizeTranscriptFile(transcriptFile).flatMap { tokenizedTranscript ->
                val writeTmpTranscriptFile = this.writeTmpTranscriptFile(
                    tokenizedTranscript = tokenizedTranscript,
                    resultFile = Paths.get(tmpDir.absolutePathString(), "speaker.txt")
                )

                val writeTmpAudioFile = this.writeTmpAudioFile(
                    audioFile = audioFile,
                    resultFile = Paths.get(tmpDir.absolutePathString(), "speaker.wav")
                )

                Uni
                    .join()
                    .all(writeTmpTranscriptFile, writeTmpAudioFile)
                    .andFailFast()
                    .flatMap {
                        this.mfaForceAlign(corpusDir = tmpDir)
                    }.flatMap { tmpResultFile ->
                        this.mfaResultFileToSrtFile(
                            mfaResultFile = tmpResultFile[0],
                            tokenizedTranscript = tokenizedTranscript,
                            resultFile = resultFile
                        )
                    }

            }
        }
        .onFailure().invoke { e ->
            when(e) {
                is CommandLineExecutionFailedException -> logger.error("Mfa failed alignment - exit value: ${e.exitValue} - command: ${e.command} - audio file: ${audioFile.absolutePathString()} - transcript file: ${transcriptFile.absolutePathString()} - result file: ${resultFile.absolutePathString()}", e)
                else -> logger.error("Error generating subtitles - audio file: ${audioFile.absolutePathString()} - transcript file: ${transcriptFile.absolutePathString()} - result file: ${resultFile.absolutePathString()}", e)
            }
        }
        .onFailure().transform { e -> UnexpectedErrorException(message = "Error generating subtitles", cause = e) }
    }

    override fun batchGenerateSubtitles(audioFiles: List<Path>, transcriptFiles: List<Path>, resultFileProvider: (Int, Path, Path) -> Path): Uni<List<Path>> {
        if(audioFiles.size != transcriptFiles.size) {
            throw IllegalArgumentException("Audio files list and transcript files list must be of the same size")
        }

        val tmpDir = Paths.get(this.tmpDir.absolutePathString(), UUID.randomUUID().toString())

        return this.fileSystem.mkdir(tmpDir.absolutePathString()).flatMap {
            val fileIndexes = audioFiles.indices

            this.tokenizeTranscriptFiles(transcriptFiles).flatMap { tokenizedTranscripts ->
                val writeTmpAudioFiles = this.writeTmpAudioFiles(
                    audioFiles = audioFiles,
                    resultFiles = fileIndexes.map { Paths.get(tmpDir.absolutePathString(), "speaker${it}.wav") }
                )

                val writeTmpTranscriptFiles = this.writeTmpTranscriptFiles(
                    tokenizedTranscripts = tokenizedTranscripts,
                    resultFiles = fileIndexes.map { Paths.get(tmpDir.absolutePathString(), "speaker${it}.txt") }
                )

                Uni
                    .join()
                    .all(writeTmpTranscriptFiles, writeTmpAudioFiles)
                    .andFailFast()
                    .flatMap {
                        this.mfaForceAlign(corpusDir = tmpDir)
                            .map {
                                fileIndexes.map { Paths.get(tmpDir.absolutePathString(), "speaker${it}.json") }
                            }
                    }.flatMap { tmpResultFiles ->
                        this.mfaResultFilesToSrtFiles(
                            mfaResultFiles = tmpResultFiles,
                            tokenizedTranscripts = tokenizedTranscripts,
                            resultFiles = audioFiles.zip(transcriptFiles).mapIndexed { i, (audioFile, transcriptFile) ->
                                resultFileProvider.invoke(
                                    i,
                                    audioFile,
                                    transcriptFile
                                )
                            }
                        )
                    }

            }
            .onFailure().invoke { e ->
                when(e) {
                    is CommandLineExecutionFailedException -> logger.error("Mfa failed alignment - exit value: ${e.exitValue} - command: ${e.command} - audio files: ${audioFiles.map { it.absolutePathString() }} - transcript files: ${transcriptFiles.map { it.absolutePathString() }} - result file provider: $resultFileProvider", e)
                    else -> logger.error("Error generating subtitles - audio files: ${audioFiles.map { it.absolutePathString() }} - transcript files: ${transcriptFiles.map { it.absolutePathString() }} - result file provider: $resultFileProvider", e)
                }
            }
            .onFailure().transform { e -> UnexpectedErrorException(message = "Error generating subtitles", cause = e) }
        }
    }

    private fun tokenizeTranscriptFiles(transcriptFiles: List<Path>): Uni<List<List<String>>> {
        return this.batchExecute {
            transcriptFiles.map {
                this.tokenizeTranscriptFile(transcriptFile = it)
            }
        }
    }

    private fun tokenizeTranscriptFile(transcriptFile: Path): Uni<List<String>> {
        return this.fileSystem.readFile(transcriptFile.absolutePathString())
            .map {
                it.toString(Charsets.UTF_8)
            }.map { transcriptContent ->
                this.tokenize(text = transcriptContent)
            }
    }

    private fun tokenize(text: String): List<String> {
        return text.split(' ', '\t', '\n', '\r', '\u000B', '\u000C').filter { it.isNotBlank() && it.contains("\\w".toRegex()) }
    }

    private fun writeTmpTranscriptFiles(tokenizedTranscripts: List<List<String>>, resultFiles: List<Path>): Uni<List<Void>> {
        return this.batchExecute {
            tokenizedTranscripts.zip(resultFiles).map { (tokenizedTranscript, resultFile) ->
                this.writeTmpTranscriptFile(tokenizedTranscript = tokenizedTranscript, resultFile = resultFile)
            }
        }
    }

    private fun writeTmpTranscriptFile(tokenizedTranscript: List<String>, resultFile: Path): Uni<Void> {
        val resultFileContent = this.normalizeTokensForMfa(tokens = tokenizedTranscript).joinToString(" ")
        return this.fileSystem.writeFile(resultFile.absolutePathString(), Buffer.buffer(resultFileContent))
    }

    private fun writeTmpAudioFiles(audioFiles: List<Path>, resultFiles: List<Path>): Uni<List<Void>> {
        return this.batchExecute {
            audioFiles.zip(resultFiles).map { (audioFile, resultFile) ->
                this.writeTmpAudioFile(audioFile = audioFile, resultFile = resultFile)
            }
        }
    }

    private fun normalizeTokensForMfa(tokens: List<String>): List<String> {
        return tokens.map {
            it
                .trim('*')
                .replace(regex = """[()\[\]{}]""".toRegex(), replacement = "")
                .replace('*', '_')
        }
    }

    private fun writeTmpAudioFile(audioFile: Path, resultFile: Path): Uni<Void> {
        return this.fileSystem.copy(audioFile.absolutePathString(), resultFile.absolutePathString())
    }

    private fun mfaForceAlign(corpusDir: Path): Uni<List<Path>> {
        val command = this.mfaCommandBuilder.buildAlignCommand(corpusDir = corpusDir)

        return this.commandLineExecutor.executeCommand(command = command)
            .flatMap {
                this.fileSystem.readDir(corpusDir.absolutePathString(), ".*\\.json")
                    .map { mfaResultFilePaths ->
                        mfaResultFilePaths.map { mfaResultFilePath -> Paths.get(mfaResultFilePath) }
                    }
            }
    }

    private fun mfaResultFilesToSrtFiles(mfaResultFiles: List<Path>, tokenizedTranscripts: List<List<String>>, resultFiles: List<Path>): Uni<List<Path>> {
        return this.batchExecute {
            val args = mutableListOf<Triple<Path, List<String>, Path>>()
            for(i in resultFiles.indices) {
                args.add(Triple(mfaResultFiles[i], tokenizedTranscripts[i], resultFiles[i]))
            }
            args.map { (mfaResultFile, tokenizedTranscript, resultFile) ->
                this.mfaResultFileToSrtFile(mfaResultFile = mfaResultFile, tokenizedTranscript = tokenizedTranscript, resultFile = resultFile)
            }
        }
    }

    private fun mfaResultFileToSrtFile(mfaResultFile: Path, tokenizedTranscript: List<String>, resultFile: Path): Uni<Path> {
        return this.fileSystem.readFile(mfaResultFile.absolutePathString()).map { it.toString(Charsets.UTF_8) }.map {
                this.mfaResultToSrt(mfaResult = JsonObject(it), tokens = tokenizedTranscript)
            }.flatMap {
                this.fileSystem.writeFile(resultFile.absolutePathString(), Buffer.buffer(it))
            }.map { resultFile }
    }

    private fun mfaResultToSrt(mfaResult: JsonObject, tokens: List<String>): String {
        var result = ""

        val entries: JsonArray =
            mfaResult
                .getJsonObject("tiers")
                .getJsonObject("words")
                .getJsonArray("entries")

        var j = 0
        for((i, token) in tokens.withIndex()) {
            var entry: JsonArray
            var start: Double
            var end: Double
            var word: String

            do {
                entry = entries.getJsonArray(j)
                start = entry.getDouble(0)
                end = entry.getDouble(1)
                word = entry.getString(2)
                j++
            } while(j < entries.size() && word == "<eps>")

            if(token.contains('-') && token != word) {
                entry = entries.getJsonArray(j)
                word += "-${entry.getString(2)}"
                end = entry.getDouble(1)
                j++
            }

            result += """
                |${i + 1}
                |${this.formatToTimestamp(secondsAsDouble = start)} --> ${this.formatToTimestamp(secondsAsDouble = end)}
                |${token}
            """.trimEnd(' ')
        }

        if(j != entries.size()) {
            throw IllegalStateException("MFA response not entirely processed. Please review the text and normalize it")
        }

        return result.trimMargin()
    }

    private fun formatToTimestamp(secondsAsDouble: Double): String {
        val totalMillis = (secondsAsDouble * 1000).toLong()

        val hours = TimeUnit.MILLISECONDS.toHours(totalMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(totalMillis) % 60
        val millis = totalMillis % 1000

        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    private fun <T> batchExecute(toExecute: Supplier<List<Uni<T>>>): Uni<List<T>> {
        return Uni.join().all(
            toExecute.get()
        ).usingConcurrencyOf(this.batchSize).andFailFast()
    }

    companion object MfaSubtitlesService {
        private val logger = Logger.getLogger(MfaSubtitleService::class.java.name)
    }
}