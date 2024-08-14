package com.hoseus

import com.hoseus.media.DockerizedFfmpegCommandBuilder
import com.hoseus.media.LocalFfmpegCommandBuilder
import com.hoseus.media.MediaEditorService
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
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FfmpegMediaEditorServiceTest {
    @ConfigProperty(name = "app.media.tmp.dir")
    private lateinit var tmpDir: Path

    private lateinit var testTmpDir: Path

    @ConfigProperty(name = "app.subtitle.batch.size")
    private var batchSize: Int = 0

    @ConfigProperty(name = "app.subtitle.mfa.beam")
    private var beam: Int = 0

    @Inject
    private lateinit var ffmpegMediaEditorService: MediaEditorService

    private lateinit var dockerizedFfmpegCommandBuilder: DockerizedFfmpegCommandBuilder

    private lateinit var localFfmpegCommandBuilder: LocalFfmpegCommandBuilder

    private val subscriptionTimeout = 5.toDuration(DurationUnit.MINUTES).toJavaDuration()

    @BeforeAll
    fun beforeAll() {
        this.testTmpDir = Files.createTempDirectory(this.tmpDir, null).toAbsolutePath()

        this.dockerizedFfmpegCommandBuilder = DockerizedFfmpegCommandBuilder()

        this.localFfmpegCommandBuilder = LocalFfmpegCommandBuilder()
    }

    @AfterAll
    fun afterAll() {
        this.testTmpDir.listDirectoryEntries().forEach {
            it.deleteIfExists()
        }

        this.testTmpDir.deleteIfExists()
    }

    @Test
    fun createTmpVideoTest() {
        val baseFileName = UUID.randomUUID().toString()

        val audioFile = ResourceLoader.loadResource("/single/single_audio.wav").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.wav"),
                inputStream!!.readAllBytes()
            )
        }

        val subtitleFile = ResourceLoader.loadResource("/single/single_subtitle.srt").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.srt"),
                inputStream!!.readAllBytes()
            )
        }

        val resultFile = Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.mp4")

        this.ffmpegMediaEditorService.createTmpVideo(audioFile = audioFile, subtitleFile = subtitleFile, resultFile = resultFile)
            .invoke { it -> Assertions.assertTrue(it.exists()) }
            .invoke { it -> Assertions.assertEquals(resultFile.toAbsolutePath(), it.toAbsolutePath()) }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(this.subscriptionTimeout)
            .assertCompleted()
    }

    @Test
    fun batchCreateTmpVideoTest() {
        val baseFileName = UUID.randomUUID().toString()

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

        val subtitleFiles = listOf(
            ResourceLoader.loadResource("/batch/batch_subtitle_part_0.srt").use { inputStream ->
                Files.write(
                    Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_part_0.srt"),
                    inputStream!!.readAllBytes()
                )
            },
            ResourceLoader.loadResource("/batch/batch_subtitle_part_1.srt").use { inputStream ->
                Files.write(
                    Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_part_1.srt"),
                    inputStream!!.readAllBytes()
                )
            }
        )

        val resultFileProvider = { i: Int, _: Path, _: Path -> Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_part_${i}.mp4") }
        val resultFiles = audioFiles.indices.map { i -> resultFileProvider.invoke(i, Paths.get(""), Paths.get("")) }

        this.ffmpegMediaEditorService.batchCreateTmpVideo(audioFiles = audioFiles, subtitleFiles = subtitleFiles, resultFileProvider = resultFileProvider)
            .invoke { tmpVideoFiles ->
                tmpVideoFiles.forEachIndexed { i, tmpVideoFile ->
                    Assertions.assertTrue(tmpVideoFile.exists())
                    Assertions.assertEquals(resultFiles[i].toAbsolutePath(), tmpVideoFile.toAbsolutePath())
                }
            }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(this.subscriptionTimeout)
            .assertCompleted()
    }

    @Test
    fun concatFilesTest() {
        val baseFileName = UUID.randomUUID().toString()

        val tmpVideoFiles = listOf(
            ResourceLoader.loadResource("/batch/batch_tmp_video_part_0.mp4").use { inputStream ->
                Files.write(
                    Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_part_0.mp4"),
                    inputStream!!.readAllBytes()
                )
            },
            ResourceLoader.loadResource("/batch/batch_tmp_video_part_1.mp4").use { inputStream ->
                Files.write(
                    Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_part_1.mp4"),
                    inputStream!!.readAllBytes()
                )
            }
        )

        val resultFile = Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.mp4")

        this.ffmpegMediaEditorService.concatFiles(files = tmpVideoFiles, resultFile = resultFile, interleaveFile = null)
            .invoke { it -> Assertions.assertTrue(it.exists()) }
            .invoke { it -> Assertions.assertEquals(resultFile.toAbsolutePath(), it.toAbsolutePath()) }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(this.subscriptionTimeout)
            .assertCompleted()
    }

    @Test
    fun getDurationTest() {
        val baseFileName = UUID.randomUUID().toString()

        val tmpVideoFile = ResourceLoader.loadResource("/single/single_tmp_video.mp4").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.wav"),
                inputStream!!.readAllBytes()
            )
        }

        this.ffmpegMediaEditorService.getDuration(file = tmpVideoFile)
            .invoke { duration ->
                Assertions.assertEquals(Duration.parse("4.587333s"), duration)
            }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(this.subscriptionTimeout)
            .assertCompleted()
    }

    @Test
    fun createFinalVideoTest() {
        val baseFileName = UUID.randomUUID().toString()

        val tmpVideo = ResourceLoader.loadResource("/single/single_tmp_video.mp4").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_tmp_video.mp4"),
                inputStream!!.readAllBytes()
            )
        }

        val visualsVideo = ResourceLoader.loadResource("/single/single_visuals_video.mp4").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_visuals_video.mp4"),
                inputStream!!.readAllBytes()
            )
        }

        val resultFile = Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.mp4")

        this.ffmpegMediaEditorService.createFinalVideo(originalVideoFile = tmpVideo, newVideoFile = visualsVideo, resultFile = resultFile)
            .invoke { it -> Assertions.assertTrue(it.exists()) }
            .invoke { it -> Assertions.assertEquals(resultFile.toAbsolutePath(), it.toAbsolutePath()) }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(this.subscriptionTimeout)
            .assertCompleted()
    }

    @Test
    fun buildDockerizedCreateTmpVideoCommandTest() {
        val baseFileName = UUID.randomUUID().toString()

        val audioFile = ResourceLoader.loadResource("/single/single_audio.wav").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.wav"),
                inputStream!!.readAllBytes()
            )
        }

        val subtitleFile = ResourceLoader.loadResource("/single/single_subtitle.srt").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.srt"),
                inputStream!!.readAllBytes()
            )
        }

        val resultFile = Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.mp4")

        val result = this.dockerizedFfmpegCommandBuilder.buildCreateTmpVideoCommand(
            audioFile = audioFile,
            subtitleFile = subtitleFile,
            resultFile = resultFile
        )

        val expectedResult = listOfNotNull(
            "docker",
            "run",
            "--rm",
            "-v", "${audioFile.absolutePathString()}:/input/${audioFile.name}",
            "-v", "${subtitleFile.absolutePathString()}:/input/${subtitleFile.name}",
            "-v", "${resultFile.toAbsolutePath().parent.absolutePathString()}:/output",
            "lscr.io/linuxserver/ffmpeg:latest",
            "-i", "/input/${audioFile.name}",
            "-i", "/input/${subtitleFile.name}",
            "-map", "0:a",
            "-map", "1:s",
            "-c:a", "copy",
            "-c:s", "mov_text",
            "-y",
            "/output/${resultFile.name}"
        )

        Assertions.assertEquals(expectedResult, result)
    }

    @Test
    fun buildLocalCreateTmpVideoCommandTest() {
        val baseFileName = UUID.randomUUID().toString()

        val audioFile = ResourceLoader.loadResource("/single/single_audio.wav").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.wav"),
                inputStream!!.readAllBytes()
            )
        }

        val subtitleFile = ResourceLoader.loadResource("/single/single_subtitle.srt").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.srt"),
                inputStream!!.readAllBytes()
            )
        }

        val resultFile = Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.mp4")

        val result = this.localFfmpegCommandBuilder.buildCreateTmpVideoCommand(
            audioFile = audioFile,
            subtitleFile = subtitleFile,
            resultFile = resultFile
        )

        val expectedResult = listOfNotNull(
            "ffmpeg",
            "-i", audioFile.absolutePathString(),
            "-i", subtitleFile.absolutePathString(),
            "-map", "0:a",
            "-map", "1:s",
            "-c:a", "copy",
            "-c:s", "mov_text",
            "-y",
            resultFile.absolutePathString()
        )

        Assertions.assertEquals(expectedResult, result)
    }

    @Test
    fun buildDockerizedConcatFilesCommandTest() {
        val baseFileName = UUID.randomUUID().toString()

        val filesDir = Paths.get("test/resources/batch")

        val concatFile = ResourceLoader.loadResource("/batch/tmp_videos_concat_file.txt").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_concat_file.txt"),
                inputStream!!.readAllBytes()
            )
        }

        val resultFile = Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.mp4")

        val result = this.dockerizedFfmpegCommandBuilder.buildConcatFilesCommand(
            filesDir = filesDir,
            concatFile = concatFile,
            resultFile = resultFile,
            interleaveFile = null
        )

        val expectedResult = listOfNotNull(
            "docker",
            "run",
            "--rm",
            "-v", "${filesDir.absolutePathString()}:/input/${filesDir.name}",
            "-v", "${concatFile.absolutePathString()}:/tmp/${concatFile.name}",
            "-v", "${resultFile.toAbsolutePath().parent.absolutePathString()}:/output",
            "lscr.io/linuxserver/ffmpeg:latest",
            "-f", "concat",
            "-safe", "0",
            "-i", "/tmp/${concatFile.name}",
            "-c:v", "copy",
            "-c:a", "copy",
            "-c:s", "mov_text",
            "-y",
            "/output/${resultFile.name}"
        )

        Assertions.assertEquals(expectedResult, result)
    }

    @Test
    fun buildLocalConcatFilesCommandTest() {
        val baseFileName = UUID.randomUUID().toString()

        val filesDir = Paths.get("test/resources/batch")

        val concatFile = ResourceLoader.loadResource("/batch/tmp_videos_concat_file.txt").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_concat_file.txt"),
                inputStream!!.readAllBytes()
            )
        }

        val resultFile = Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.mp4")

        val result = this.localFfmpegCommandBuilder.buildConcatFilesCommand(
            filesDir = filesDir,
            concatFile = concatFile,
            resultFile = resultFile,
            interleaveFile = null
        )

        val expectedResult = listOfNotNull(
            "ffmpeg",
            "-f", "concat",
            "-safe", "0",
            "-i", concatFile.absolutePathString(),
            "-c:v", "copy",
            "-c:a", "copy",
            "-c:s", "mov_text",
            "-y",
            resultFile.absolutePathString()
        )

        Assertions.assertEquals(expectedResult, result)
    }

    @Test
    fun buildDockerizedGetDurationCommandTest() {
        val baseFileName = UUID.randomUUID().toString()

        val file = ResourceLoader.loadResource("/single/single_tmp_video.mp4").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.mp4"),
                inputStream!!.readAllBytes()
            )
        }

        val result = this.dockerizedFfmpegCommandBuilder.buildGetDurationCommand(file = file)

        val expectedResult = listOfNotNull(
            "docker",
            "run",
            "--rm",
            "-v", "${file.absolutePathString()}:/input/${file.name}",
            "--entrypoint", "ffprobe",
            "lscr.io/linuxserver/ffmpeg:latest",
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            "/input/${file.name}"
        )

        Assertions.assertEquals(expectedResult, result)
    }

    @Test
    fun buildLocalGetDurationCommandTest() {
        val baseFileName = UUID.randomUUID().toString()

        val file = ResourceLoader.loadResource("/single/single_tmp_video.mp4").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.mp4"),
                inputStream!!.readAllBytes()
            )
        }

        val result = this.localFfmpegCommandBuilder.buildGetDurationCommand(file = file)

        val expectedResult = listOfNotNull(
            "ffprobe",
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            file.absolutePathString()
        )

        Assertions.assertEquals(expectedResult, result)
    }

    @Test
    fun buildDockerizedCreateFinalVideoCommandTest() {
        val baseFileName = UUID.randomUUID().toString()

        val tmpVideoFile = ResourceLoader.loadResource("/single/single_tmp_video.mp4").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_tmp_video.mp4"),
                inputStream!!.readAllBytes()
            )
        }

        val visualsVideoFile = ResourceLoader.loadResource("/single/single_visuals_video.mp4").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_visuals_video.mp4"),
                inputStream!!.readAllBytes()
            )
        }

        val startFrom = 3

        val resultFile = Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.mp4")

        val result = this.dockerizedFfmpegCommandBuilder.buildCreateFinalVideoCommand(
            originalVideoFile = tmpVideoFile,
            newVideoFile = visualsVideoFile,
            takeNewVideoFromSeconds = startFrom,
            resultFile = resultFile
        )

        val expectedResult = listOfNotNull(
            "docker",
            "run",
            "--rm",
            "-v", "${tmpVideoFile.absolutePathString()}:/input/${tmpVideoFile.name}",
            "-v", "${visualsVideoFile.absolutePathString()}:/input/${visualsVideoFile.name}",
            "-v", "${resultFile.toAbsolutePath().parent.absolutePathString()}:/output",
            "lscr.io/linuxserver/ffmpeg:latest",
            "-ss", startFrom.toString(),
            "-i", "/input/${visualsVideoFile.name}",
            "-i", "/input/${tmpVideoFile.name}",
            "-map", "0:v",
            "-map", "1:a",
            "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-movflags", "+faststart",
            "-c:a", "aac", "-b:a", "192k", "-ac", "2",
            "-vf", "subtitles=/input/${tmpVideoFile.name}:force_style=FontName='Lobster Two,Fontsize=28,Alignment=2,MarginV=20,PrimaryColour=&H0000A5FF'",
            "-shortest",
            "-y",
            "/output/${resultFile.name}"
        )

        Assertions.assertEquals(expectedResult, result)
    }

    @Test
    fun buildLocalCreateFinalVideoCommandTest() {
        val baseFileName = UUID.randomUUID().toString()

        val tmpVideoFile = ResourceLoader.loadResource("/single/single_tmp_video.mp4").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_tmp_video.mp4"),
                inputStream!!.readAllBytes()
            )
        }

        val visualsVideoFile = ResourceLoader.loadResource("/single/single_visuals_video.mp4").use { inputStream ->
            Files.write(
                Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}_visuals_video.mp4"),
                inputStream!!.readAllBytes()
            )
        }

        val startFrom = 3

        val resultFile = Paths.get(this.testTmpDir.absolutePathString(), "${baseFileName}.mp4")

        val result = this.localFfmpegCommandBuilder.buildCreateFinalVideoCommand(
            originalVideoFile = tmpVideoFile,
            newVideoFile = visualsVideoFile,
            takeNewVideoFromSeconds = startFrom,
            resultFile = resultFile
        )

        val expectedResult = listOfNotNull(
            "ffmpeg",
            "-ss", startFrom.toString(),
            "-i", visualsVideoFile.absolutePathString(),
            "-i", tmpVideoFile.absolutePathString(),
            "-map", "0:v",
            "-map", "1:a",
            "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-movflags", "+faststart",
            "-c:a", "aac", "-b:a", "192k", "-ac", "2",
            "-vf", "subtitles=${tmpVideoFile.absolutePathString()}:force_style=FontName='Lobster Two,Fontsize=28,Alignment=2,MarginV=20,PrimaryColour=&H0000A5FF'",
            "-shortest",
            "-y",
            resultFile.absolutePathString()
        )

        Assertions.assertEquals(expectedResult, result)
    }
}