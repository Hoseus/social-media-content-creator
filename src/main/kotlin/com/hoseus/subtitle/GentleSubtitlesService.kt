package com.hoseus.subtitle

import io.quarkus.arc.properties.IfBuildProperty
import io.smallrye.mutiny.Uni
import io.vertx.mutiny.core.buffer.Buffer
import io.vertx.mutiny.core.file.FileSystem
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString

@ApplicationScoped
@IfBuildProperty(name = "app.subtitle.implementation", stringValue = "gentle", enableIfMissing = false)
class GentleSubtitlesService(
    private val gentleRestClient: GentleRestClient,
    private val fileSystem: FileSystem
) : SubtitlesService {
    override fun generateSubtitles(audioFile: Path, transcriptFile: Path, resultFile: Path): Uni<Path> {
        return this.gentleRestClient.forceAlignment(audioFile = audioFile, transcriptFile = transcriptFile)
            .flatMap { gentleResponse ->
                this.fileSystem.readFile(transcriptFile.absolutePathString())
                    .map {
                        it.toString(Charsets.UTF_8)
                    }.map { transcriptContent ->
                        this.gentleResponseToSrt(gentleResponse = gentleResponse, tokens = this.tokenize(text = transcriptContent))
                    }.flatMap {
                        this.fileSystem.writeFile(
                            resultFile.absolutePathString(),
                            Buffer.buffer(it)
                        )
                    }.map { resultFile }
            }
            .onFailure().invoke { e -> logger.error("Error generating subtitles", e) }
    }

    override fun batchGenerateSubtitles(audioFiles: List<Path>, transcriptFiles: List<Path>, resultFileProvider: (Int, Path, Path) -> Path): Uni<List<Path>> {
        val subtitleFiles = audioFiles.zip(transcriptFiles)
            .mapIndexed { i, (audioFile, transcriptFile) ->
                this.generateSubtitles(
                    audioFile = audioFile,
                    transcriptFile = transcriptFile,
                    resultFile = resultFileProvider.invoke(i, audioFile, transcriptFile)
                )
            }

        return Uni
            .join()
            .all(subtitleFiles)
            .usingConcurrencyOf(2)
            .andFailFast()
    }

    private fun tokenize(text: String): List<String> {
        return text.split(' ', '\t', '\n', '\r', '\u000B', '\u000C').filter { it.isNotBlank() }
    }

    private fun gentleResponseToSrt(gentleResponse: GentleResponse, tokens: List<String>): String {
        var result = ""

        for((i, wordAlignment) in gentleResponse.words.withIndex()) {
            result += """
                |${i + 1}
                |${this.formatToTimestamp(secondsAsDouble = wordAlignment.start)} --> ${this.formatToTimestamp(secondsAsDouble = wordAlignment.end)}
                |${wordAlignment.word}
            """.trimEnd(' ')
        }

        return result.trimMargin()
    }

    fun formatToTimestamp(secondsAsDouble: Double): String {
        val totalMillis = (secondsAsDouble * 1000).toLong()

        val hours = TimeUnit.MILLISECONDS.toHours(totalMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(totalMillis) % 60
        val millis = totalMillis % 1000

        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    companion object GentleSubtitlesService {
        private val logger = Logger.getLogger(GentleSubtitlesService::class.java.name)
    }
}