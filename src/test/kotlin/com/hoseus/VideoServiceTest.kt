package com.hoseus

import com.hoseus.video.VideoService
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

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VideoServiceTest {
    @ConfigProperty(name = "app.media.tmp.dir")
    private lateinit var tmpDir: Path

    private lateinit var testTmpDir: Path

    @Inject
    private lateinit var videoService: VideoService

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
    fun getRandomMp4VideoTest() {
        val resultFile = Paths.get(this.testTmpDir.absolutePathString(), "${UUID.randomUUID()}.mp4")

        this.videoService.getRandomMp4Video(resultFile = resultFile)
            .invoke { it -> Assertions.assertTrue(it.exists()) }
            .invoke { it -> Assertions.assertEquals(resultFile.toAbsolutePath(), it.toAbsolutePath()) }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
    }
}