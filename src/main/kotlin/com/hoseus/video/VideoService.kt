package com.hoseus.video

import io.smallrye.mutiny.Uni
import io.vertx.mutiny.core.file.FileSystem
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

@ApplicationScoped
class VideoService(
    @ConfigProperty(name = "app.media.input.videos-dir") private val inputVideoDir: Path,
    private val fileSystem: FileSystem
) {
    fun getRandomMp4Video(resultFile: Path): Uni<Path> {
        return this.fileSystem
            .readDir(this.inputVideoDir.absolutePathString(), ".*\\.mp4")
            .map { Paths.get(it.random()) }
            .flatMap{ this.fileSystem.copy(it.absolutePathString(), resultFile.absolutePathString()) }
            .map { resultFile }
    }
}
