package com.hoseus.tts

import com.hoseus.cli.CommandLineExecutor
import com.hoseus.exception.CommandLineExecutionFailedException
import com.hoseus.exception.UnexpectedErrorException
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@ApplicationScoped
class CoquiTtsService(
    @ConfigProperty(name = "app.tts.batch.size") private val batchSize: Int,
    private val coquiTtsCommandBuilder: CoquiTtsCommandBuilder,
    private val commandLineExecutor: CommandLineExecutor
) : TtsService {

    override fun batchTts(texts: List<String>, speakerSex: SpeakerSex, resultFileProvider: (Int, String, SpeakerSex) -> Path): Uni<List<Path>> {
        val audioFiles = texts.mapIndexed { i, text ->
            this.tts(text = text, speakerSex = speakerSex, resultFile = resultFileProvider.invoke(i, text, speakerSex))
        }

        return Uni
            .join()
            .all(audioFiles)
            .usingConcurrencyOf(this.batchSize)
            .andFailFast()
    }

    override fun tts(text: String, speakerSex: SpeakerSex, resultFile: Path): Uni<Path> {
        val command = this.coquiTtsCommandBuilder.buildSynthesizeSpeechCommand(text = text, speakerSex = speakerSex, resultFile = resultFile)

        return this.commandLineExecutor.executeCommand(command = command)
            .map { resultFile }
            .onFailure().invoke { e ->
                when(e) {
                    is CommandLineExecutionFailedException -> logger.error("Coqui tts failed to synthesize speech - exit value: ${e.exitValue} - command: ${e.command} - text: \"$text\" - result file: ${resultFile.absolutePathString()}", e)
                    else -> logger.error("Error generating speech from text - text: \"$text\" - result file: ${resultFile.absolutePathString()}", e)
                }
            }
            .onFailure().transform { e -> UnexpectedErrorException(message = "Error generating speech from text", cause = e) }
    }

    companion object CoquiTtsService {
        private val logger = Logger.getLogger(CoquiTtsService::class.java.name)
    }
}