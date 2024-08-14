package com.hoseus.transcript

import io.smallrye.mutiny.Uni
import io.vertx.mutiny.core.buffer.Buffer
import io.vertx.mutiny.core.file.FileSystem
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@ApplicationScoped
class TranscriptService(
    @ConfigProperty(name = "app.transcript.batch.size") private val batchSize: Int,
    private val fileSystem: FileSystem
) {
    fun writeTranscriptToTxt(text: String, resultFile: Path): Uni<Path> {
        return this.fileSystem
            .writeFile(resultFile.absolutePathString(), Buffer.buffer(text))
            .map { resultFile }
            .onFailure().invoke { e -> logger.error("Error writing transcript - File: ${resultFile.absolutePathString()}", e) }
    }

    fun batchWriteTranscriptsToTxt(texts: List<String>, resultFileProvider: (Int, String) -> Path): Uni<List<Path>> {
        val transcriptFiles = texts.mapIndexed { i, text -> this.writeTranscriptToTxt(text, resultFileProvider.invoke(i, text)) }

        return Uni
            .join()
            .all(transcriptFiles)
            .usingConcurrencyOf(this.batchSize)
            .andFailFast()
    }

    companion object TranscriptService {
        private val logger = Logger.getLogger(TranscriptService::class.java.name)
    }
}