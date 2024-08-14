package com.hoseus

import com.hoseus.tts.CoquiTtsService
import com.hoseus.tts.DockerizedCoquiTtsCommandBuilder
import com.hoseus.tts.LocalCoquiTtsCommandBuilder
import com.hoseus.tts.SpeakerSex
import io.quarkus.test.junit.QuarkusTest
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoquiTtsServiceTest {
    @ConfigProperty(name = "app.media.tmp.dir")
    private lateinit var tmpDir: Path

    private lateinit var testTmpDir: Path

    @ConfigProperty(name = "app.tts.coqui-tts.model-name")
    private lateinit var modelName: String

    @ConfigProperty(name = "app.tts.coqui-tts.female-speaker-idx")
    private lateinit var femaleSpeakerIdx: String

    @ConfigProperty(name = "app.tts.coqui-tts.male-speaker-idx")
    private lateinit var maleSpeakerIdx: String

    @ConfigProperty(name = "app.tts.coqui-tts.use-cuda")
    private var useCuda: Boolean = true

    @Inject
    private lateinit var coquiTtsService: CoquiTtsService

    private lateinit var dockerizedCoquiTtsCommandBuilder: DockerizedCoquiTtsCommandBuilder

    private lateinit var localCoquiTtsCommandBuilder: LocalCoquiTtsCommandBuilder

    private val subscriptionTimeout = 5.toDuration(DurationUnit.MINUTES).toJavaDuration()
    private val modelsCacheVolume: String = "coqui-tts-models-cache"

    @BeforeAll
    fun beforeAll() {
        this.testTmpDir = Files.createTempDirectory(this.tmpDir, null).toAbsolutePath()

        this.dockerizedCoquiTtsCommandBuilder = DockerizedCoquiTtsCommandBuilder(
            modelName = this.modelName,
            femaleSpeakerIdx = this.femaleSpeakerIdx,
            maleSpeakerIdx = this.maleSpeakerIdx,
            useCuda = this.useCuda
        )

        this.localCoquiTtsCommandBuilder = LocalCoquiTtsCommandBuilder(
            modelName = this.modelName,
            femaleSpeakerIdx = this.femaleSpeakerIdx,
            maleSpeakerIdx = this.maleSpeakerIdx,
            useCuda = this.useCuda
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
    fun ttsTest() {
        val text = """
            |My brother was conspiring with my cousin to keep the inheritance all to themselves
            |""".trimMargin().replace('\n', ' ').trim()

        val speakerSex = SpeakerSex.F

        val resultFile = Paths.get(this.testTmpDir.absolutePathString(), "${UUID.randomUUID()}.wav")

        this.coquiTtsService.tts(text = text, speakerSex = speakerSex, resultFile = resultFile)
            .invoke { it -> Assertions.assertTrue(it.exists()) }
            .invoke { it -> Assertions.assertEquals(resultFile.toAbsolutePath(), it.toAbsolutePath()) }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(this.subscriptionTimeout)
            .assertCompleted()
    }

    @Test
    fun batchTtsTest() {
        val texts = listOf(
            """
            |My brother was conspiring with my cousin to keep the inheritance all to themselves
            |""".trimMargin().replace('\n', ' ').trim(),
            """
            |My MIL always hated me, I don't know why, maybe because she felt that I was stealing her son
            |""".trimMargin().replace('\n', ' ').trim()
        )

        val speakerSex = SpeakerSex.F

        val baseFileName = UUID.randomUUID().toString()

        val resultFileProvider = { i: Int, _: String, _: SpeakerSex -> Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_part_${i}.wav") }
        val resultFiles = texts.indices.map { i -> resultFileProvider.invoke(i, texts[i], speakerSex) }

        this.coquiTtsService.batchTts(texts = texts, speakerSex = speakerSex, resultFileProvider = resultFileProvider)
            .invoke { audioFiles ->
                audioFiles.forEachIndexed { i, audioFile ->
                    Assertions.assertTrue(audioFile.exists())
                    Assertions.assertEquals(resultFiles[i].toAbsolutePath(), audioFile.toAbsolutePath())
                }
            }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(this.subscriptionTimeout)
            .assertCompleted()
    }

    @ParameterizedTest
    @EnumSource(value = SpeakerSex::class)
    fun buildDockerizedCommandTest(speakerSex: SpeakerSex) {
        val text = """
            |My brother was conspiring with my cousin to keep the inheritance all to themselves
            |""".trimMargin().replace('\n', ' ').trim()

        val resultFile = Paths.get(UUID.randomUUID().toString())

        val result = this.dockerizedCoquiTtsCommandBuilder.buildSynthesizeSpeechCommand(text = text, speakerSex = speakerSex, resultFile = resultFile)

        val expectedResult = listOfNotNull(
            "docker",
            "run",
            "--rm",
            "-e", "COQUI_TOS_AGREED=1",
            "-v", "${this.modelsCacheVolume}:/root/.local/share/tts",
            "-v", "${resultFile.toAbsolutePath().parent.absolutePathString()}:/root/tts-output",
            "--gpus", "all",
            "ghcr.io/coqui-ai/tts:latest",
            "--model_name", this.modelName,
            "--speaker_idx", if(speakerSex == SpeakerSex.F) this.femaleSpeakerIdx else maleSpeakerIdx,
            "--language_idx", "en",
            "--text", "\"$text\"",
            "--out_path", "/root/tts-output/${resultFile.name}",
            "--use_cuda", this.useCuda.toString()
        )

        Assertions.assertEquals(expectedResult, result)
    }

    @ParameterizedTest
    @EnumSource(value = SpeakerSex::class)
    fun buildLocalCommandTest(speakerSex: SpeakerSex) {
        val text = """
            |My brother was conspiring with my cousin to keep the inheritance all to themselves
            |""".trimMargin().replace('\n', ' ').trim()

        val resultFile = Paths.get(UUID.randomUUID().toString())

        val result = this.localCoquiTtsCommandBuilder.buildSynthesizeSpeechCommand(text = text, speakerSex = speakerSex, resultFile = resultFile)

        val expectedResult = listOfNotNull(
            "tts",
            "--model_name", this.modelName,
            "--speaker_idx", if(speakerSex == SpeakerSex.F) this.femaleSpeakerIdx else maleSpeakerIdx,
            "--language_idx", "en",
            "--text", "\"$text\"",
            "--out_path", resultFile.absolutePathString(),
            if(this.useCuda) "--use_cuda" else null
        )

        Assertions.assertEquals(expectedResult, result)
    }
}