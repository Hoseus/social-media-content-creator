package com.hoseus

import com.hoseus.subtitle.DockerizedMfaCommandBuilder
import com.hoseus.subtitle.LocalMfaCommandBuilder
import com.hoseus.subtitle.MfaSubtitleService
import com.hoseus.util.ResourceLoader
import io.quarkus.test.junit.QuarkusTest
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MfaSubtitleServiceTest {
    @ConfigProperty(name = "app.media.tmp.dir")
    private lateinit var tmpDir: Path

    private lateinit var testTmpDir: Path

    @ConfigProperty(name = "app.subtitle.batch.size")
    private var batchSize: Int = 0

    @ConfigProperty(name = "app.subtitle.mfa.beam")
    private var beam: Int = 0

    @Inject
    private lateinit var mfaSubtitleService: MfaSubtitleService

    private lateinit var dockerizedMfaCommandBuilder: DockerizedMfaCommandBuilder

    private lateinit var localMfaCommandBuilder: LocalMfaCommandBuilder

    private val subscriptionTimeout = 5.toDuration(DurationUnit.MINUTES).toJavaDuration()
    private val modelsCacheVolume: String = "mfa-models-cache"

    @BeforeAll
    fun beforeAll() {
        this.testTmpDir = Files.createTempDirectory(this.tmpDir, null).toAbsolutePath()

        this.dockerizedMfaCommandBuilder = DockerizedMfaCommandBuilder(
            batchSize = this.batchSize,
            beam = this.beam
        )

        this.localMfaCommandBuilder = LocalMfaCommandBuilder(
            batchSize = this.batchSize,
            beam = this.beam
        )
    }

    @AfterAll
    fun afterAll() {
        this.testTmpDir.listDirectoryEntries().forEach {
            it.deleteIfExists()
        }

        this.testTmpDir.deleteIfExists()
    }

    @Test
    fun generateSubtitlesTest() {
        val baseFileName = UUID.randomUUID().toString()

        val transcriptFile = ResourceLoader.loadResource("/single/single_transcript.txt").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.txt"),
                inputStream!!.readAllBytes()
            )
        }

        val audioFile = ResourceLoader.loadResource("/single/single_audio.wav").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.wav"),
                inputStream!!.readAllBytes()
            )
        }

        val resultFile = Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.srt")

        this.mfaSubtitleService.generateSubtitles(transcriptFile = transcriptFile, audioFile = audioFile, resultFile = resultFile)
            .invoke { it -> Assertions.assertTrue(it.exists()) }
            .invoke { it -> Assertions.assertEquals(resultFile.toAbsolutePath(), it.toAbsolutePath()) }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(this.subscriptionTimeout)
            .assertCompleted()
    }

    @Test
    fun batchGenerateSubtitlesTest() {
        val texts = listOf(
            """
            |My brother was conspiring with my cousin to keep the inheritance all to themselves
            |""".trimMargin().replace('\n', ' ').trim(),
            """
            |My MIL always hated me, I don't know why, maybe because she felt that I was stealing her son
            |""".trimMargin().replace('\n', ' ').trim()
        )

        val baseFileName = UUID.randomUUID().toString()

        val transcriptFiles = listOf(
            ResourceLoader.loadResource("/batch/batch_transcript_part_0.txt").use { inputStream ->
                Files.write(
                    Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_part_0.txt"),
                    inputStream!!.readAllBytes()
                )
            },
            ResourceLoader.loadResource("/batch/batch_transcript_part_1.txt").use { inputStream ->
                Files.write(
                    Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_part_1.txt"),
                    inputStream!!.readAllBytes()
                )
            }
        )

        val audioFiles = listOf(
            ResourceLoader.loadResource("/batch/batch_audio_part_0.wav").use { inputStream ->
                Files.write(
                    Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_part_0.wav"),
                    inputStream!!.readAllBytes()
                )
            },
            ResourceLoader.loadResource("/batch/batch_audio_part_1.wav").use { inputStream ->
                Files.write(
                    Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_part_1.wav"),
                    inputStream!!.readAllBytes()
                )
            }
        )

        val resultFileProvider = { i: Int, _: Path, _: Path -> Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_part_${i}.srt") }
        val resultFiles = texts.indices.map { i -> resultFileProvider.invoke(i, Paths.get(""), Paths.get("")) }

        this.mfaSubtitleService.batchGenerateSubtitles(transcriptFiles = transcriptFiles, audioFiles = audioFiles, resultFileProvider = resultFileProvider)
            .invoke { subtitleFiles ->
                subtitleFiles.forEachIndexed { i, subtitleFile ->
                    Assertions.assertTrue(subtitleFile.exists())
                    Assertions.assertEquals(resultFiles[i].toAbsolutePath(), subtitleFile.toAbsolutePath())
                }
            }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(this.subscriptionTimeout)
            .assertCompleted()
    }

    @Test
    fun buildDockerizedCommandTest() {
        val result = this.dockerizedMfaCommandBuilder.buildAlignCommand(this.testTmpDir)

        val expectedResult = listOfNotNull(
            "docker",
            "run",
            "--rm",
            "-v", "${this.modelsCacheVolume}:/mfa/pretrained_models",
            "-v", "${this.testTmpDir.absolutePathString()}:/data",
            "mmcauliffe/montreal-forced-aligner:latest",
            "mfa",
            "align",
            "--single_speaker",
            "--use_mp",
            "--num_jobs", this.batchSize.toString(),
            "--include_original_text",
            "--fine_tune",
            "--output_format", "json",
            "/data",
            "/mfa/pretrained_models/dictionary/english_us_arpa.dict",
            "/mfa/pretrained_models/acoustic/english_us_arpa.zip",
            "/data",
            "--beam", this.beam.toString()
        )

        Assertions.assertEquals(expectedResult, result)
    }

    @Test
    fun buildLocalCommandTest() {
        val result = this.localMfaCommandBuilder.buildAlignCommand(this.testTmpDir)

        val expectedResult = listOfNotNull(
            "mfa",
            "align",
            "--single_speaker",
            "--use_mp",
            "--num_jobs", this.batchSize.toString(),
            "--include_original_text",
            "--fine_tune",
            "--output_format", "json",
            this.testTmpDir.absolutePathString(),
            "/mfa/pretrained_models/dictionary/english_us_arpa.dict",
            "/mfa/pretrained_models/acoustic/english_us_arpa.zip",
            this.testTmpDir.absolutePathString(),
            "--beam", this.beam.toString()
        )

        Assertions.assertEquals(expectedResult, result)
    }
}