package com.hoseus.content

import com.hoseus.media.MediaEditorService
import com.hoseus.redditapi.RedditService
import com.hoseus.sensitivity.SensitivityService
import com.hoseus.subtitle.SubtitlesService
import com.hoseus.transcript.TranscriptService
import com.hoseus.tts.SpeakerSex
import com.hoseus.tts.TtsService
import com.hoseus.video.VideoService
import io.smallrye.mutiny.Uni
import io.vertx.mutiny.core.file.FileSystem
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.absolutePathString

@ApplicationScoped
class ContentService(
    private val redditService: RedditService,
    private val sensitivityService: SensitivityService,
    private val videoService: VideoService,
    private val transcriptService: TranscriptService,
    private val ttsService: TtsService,
    private val subtitlesService: SubtitlesService,
    private val mediaEditorService: MediaEditorService,
    private val fileSystem: FileSystem,
    @ConfigProperty(name = "app.media.tmp.dir") private val tmpDir: Path,
    @ConfigProperty(name = "app.media.output.dir") private val outputDir: Path,
) {
    fun createFromRedditDrama(postUrl: String, speakerSex: SpeakerSex): Uni<Path> {
        return this.redditService
            .getPost(postUrl = postUrl)
            .flatMap { this.createFromText(text = "${it.title}\n\n${it.text}", speakerSex = speakerSex) }
    }

    fun createFromText(text: String, speakerSex: SpeakerSex): Uni<Path> {
        return this.createFromTextWithId(id = UUID.randomUUID().toString(), text = text, speakerSex = speakerSex)
    }

    private fun createFromTextWithId(id: String, text: String, speakerSex: SpeakerSex): Uni<Path> {
        val resultDir = Paths.get(this.tmpDir.absolutePathString(), id)
        return this.fileSystem.mkdir(resultDir.absolutePathString()).flatMap {
            Uni.combine()
                .all()
                .unis(
                    this.transcriptService.writeTranscriptToTxt(
                        text,
                        Paths.get(resultDir.absolutePathString(), "original_transcript.txt")
                    ),
                    this.sensitivityService.obfuscateSensitiveWords(text)
                )
                .asTuple()
                .map { it.item2 }
                .map {
                    this.splitIntoSentences(it)
                }.flatMap { sentences ->
                    this.batchCreateTmpVideos(
                        textSegments = sentences,
                        speakerSex = speakerSex,
                        resultDir = resultDir
                    )
                }.flatMap {
                    this.joinTmpVideoFiles(
                        tmpVideoFiles = it,
                        resultDir = resultDir
                    )
                }.flatMap { tmpVideo ->
                    this.getVisualsVideo(resultDir = resultDir)
                        .flatMap {
                            this.makeFinalVideo(
                                tmpVideoFile = tmpVideo,
                                visualsVideoFile = it,
                                resultFileName = id
                            )
                        }
                }
        }.onFailure().invoke { e ->
            logger.error("Error processing creation of content - id: $id", e)
        }
    }

    private fun splitIntoSentences(text: String): List<String> {
        return text.split('\n', '.').filter { it.isNotBlank() }
    }

    private fun batchCreateTmpVideos(textSegments: List<String>, speakerSex: SpeakerSex, resultDir: Path): Uni<List<Path>> {
        val transcriptFiles = this.batchCreateTranscriptFiles(
            textSegments = textSegments,
            resultDir = resultDir
        )
        val audioFiles = this.batchCreateAudioFiles(
            textSegments = textSegments,
            speakerSex = speakerSex,
            resultDir = resultDir
        )

        return Uni
            .combine()
            .all()
            .unis(audioFiles, transcriptFiles)
            .asTuple()
            .flatMap { audiosAndTranscripts ->
                this.batchCreateSubtitleFiles(
                    audioFiles = audiosAndTranscripts.item1,
                    transcriptFiles = audiosAndTranscripts.item2,
                    resultDir = resultDir
                ).map { subtitles -> Pair(audiosAndTranscripts.item1, subtitles) }
            }
            .flatMap {
                this.batchCreateTmpVideoFiles(
                    audioFiles = it.first,
                    subtitleFiles = it.second,
                    resultDir = resultDir
                )
            }
    }

    private fun batchCreateTranscriptFiles(textSegments: List<String>, resultDir: Path): Uni<List<Path>> {
        return this.transcriptService
            .batchWriteTranscriptsToTxt(
                texts = textSegments,
                resultFileProvider = { i, _ -> Paths.get(resultDir.absolutePathString(), "transcript_part${i}.txt") }
            )
    }

    private fun batchCreateAudioFiles(textSegments: List<String>, speakerSex: SpeakerSex, resultDir: Path): Uni<List<Path>> {
        return this.ttsService
            .batchTts(
                texts = textSegments,
                speakerSex = speakerSex,
                resultFileProvider = { i, _, _ -> Paths.get(resultDir.absolutePathString(), "audio_part${i}.wav") }
            )
    }

    private fun batchCreateSubtitleFiles(audioFiles: List<Path>, transcriptFiles: List<Path>, resultDir: Path): Uni<List<Path>> {
        return this.subtitlesService
            .batchGenerateSubtitles(
                audioFiles = audioFiles,
                transcriptFiles = transcriptFiles,
                resultFileProvider = { i, _, _ -> Paths.get(resultDir.absolutePathString(), "subtitles_part${i}.srt") }
            )
    }

    private fun batchCreateTmpVideoFiles(audioFiles: List<Path>, subtitleFiles: List<Path>, resultDir: Path): Uni<List<Path>> {
        return this.mediaEditorService
            .batchCreateTmpVideo(
                audioFiles = audioFiles,
                subtitleFiles = subtitleFiles,
                resultFileProvider = { i, _, _ -> Paths.get(resultDir.absolutePathString(), "tmp_video_part${i}.mp4") }
            )
    }

    private fun joinTmpVideoFiles(tmpVideoFiles: List<Path>, resultDir: Path): Uni<Path> {
        return this.mediaEditorService.concatFiles(
            files = tmpVideoFiles,
            resultFile = Paths.get(resultDir.absolutePathString(), "tmp_video.mp4")
        )
    }

    private fun getVisualsVideo(resultDir: Path): Uni<Path> {
        return this.videoService.getRandomMp4Video(
            resultFile = Paths.get(resultDir.absolutePathString(), "video_visuals.mp4")
        )
    }

    private fun makeFinalVideo(tmpVideoFile: Path, visualsVideoFile: Path, resultFileName: String): Uni<Path> {
        return this.mediaEditorService.createFinalVideo(
            originalVideoFile = tmpVideoFile,
            newVideoFile = visualsVideoFile,
            resultFile = Paths.get(this.outputDir.absolutePathString(), "${resultFileName}.mp4")
        )
    }

    companion object ContentService {
        private val logger = Logger.getLogger(ContentService::class.java)
    }
}