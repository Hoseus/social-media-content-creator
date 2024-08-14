package com.hoseus

import com.hoseus.transcript.TranscriptService
import io.quarkus.test.junit.QuarkusTest
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.*

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TranscriptServiceTest {
    @ConfigProperty(name = "app.media.tmp.dir")
    private lateinit var tmpDir: Path

    private lateinit var testTmpDir: Path

    @Inject
    private lateinit var transcriptService: TranscriptService

    @BeforeAll
    fun beforeAll() {
        this.testTmpDir = Files.createTempDirectory(this.tmpDir, null).toAbsolutePath()
    }

    @AfterAll
    fun afterAll() {
        this.testTmpDir.listDirectoryEntries().forEach {
            it.deleteIfExists()
        }

        this.testTmpDir.deleteIfExists()
    }

    @Test
    fun writeTranscriptTest() {
        val text = """
            |My brother was conspiring with my cousin to keep the inheritance all to themselves
            |""".trimMargin().replace('\n', ' ').trim()

        val resultFile = Paths.get(this.testTmpDir.absolutePathString(), "${UUID.randomUUID()}.txt")

        this.transcriptService.writeTranscriptToTxt(text = text, resultFile = resultFile)
            .invoke { it -> Assertions.assertTrue(it.exists()) }
            .invoke { it -> Assertions.assertEquals(resultFile.toAbsolutePath(), it.toAbsolutePath()) }
            .invoke { it -> Assertions.assertEquals(text, it.readText(Charsets.UTF_8)) }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
    }

    @Test
    fun batchWriteTranscriptTest() {
        val texts = listOf(
            """
            |My brother was conspiring with my cousin to keep the inheritance all to themselves
            |""".trimMargin().replace('\n', ' ').trim(),
            """
            |My MIL always hated me, I don't know why, maybe because she felt that I was stealing her son
            |""".trimMargin().replace('\n', ' ').trim()
        )

        val baseFileName = UUID.randomUUID().toString()

        val resultFileProvider = { i: Int, _: String -> Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_part_${i}.txt") }
        val resultFiles = texts.indices.map { i -> resultFileProvider.invoke(i, texts[i]) }

        this.transcriptService.batchWriteTranscriptsToTxt(texts = texts, resultFileProvider = resultFileProvider)
            .invoke { audioFiles ->
                audioFiles.forEachIndexed { i, audioFile ->
                    Assertions.assertTrue(audioFile.exists())
                    Assertions.assertEquals(resultFiles[i].toAbsolutePath(), audioFile.toAbsolutePath())
                    Assertions.assertEquals(texts[i], audioFile.readText(Charsets.UTF_8))
                }
            }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
    }
}