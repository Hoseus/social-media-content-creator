package com.hoseus.subtitle

import io.smallrye.mutiny.Uni
import io.vertx.mutiny.ext.web.client.WebClient
import io.vertx.mutiny.ext.web.multipart.MultipartForm
import jakarta.enterprise.context.ApplicationScoped
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

@ApplicationScoped
class GentleRestClient(
    private val restClient: WebClient
) {
    //curl -F "audio=@audio.mp3" -F "transcript=@words.txt" "http://localhost:8765/transcriptions?async=false"
    fun forceAlignment(audioFile: Path, transcriptFile: Path): Uni<GentleResponse> {
        return this.restClient.postAbs(
            "http://localhost:8765/transcriptions?async=false"
        ).sendMultipartForm(
            MultipartForm
                .create()
                .binaryFileUpload("audio", audioFile.name, audioFile.absolutePathString(), "audio/wav")
                .binaryFileUpload("transcript", transcriptFile.name , transcriptFile.absolutePathString(), "text/plain")
        ).map { it.bodyAsJson(GentleResponse::class.java) }
    }
}

data class GentleResponse(
    val transcript: String,
    val words: List<WordAlignment>
)

data class WordAlignment(
    val start: Double,
    val startOffset: Long,
    val end: Double,
    val endOffset: Long,
    val word: String
)