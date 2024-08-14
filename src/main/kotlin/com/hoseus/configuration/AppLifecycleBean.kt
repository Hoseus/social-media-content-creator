package com.hoseus.configuration

import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.nio.file.Path
import kotlin.io.path.createDirectories

@ApplicationScoped
class AppLifecycleBean(
    @ConfigProperty(name = "app.media.input.videos-dir") private val mediaInputVideoDir: Path,
    @ConfigProperty(name = "app.media.input.silences-dir") private val mediaInputSilencesDir: Path,
    @ConfigProperty(name = "app.media.tmp.dir") private val mediaTmpDir: Path,
    @ConfigProperty(name = "app.media.output.dir") private val mediaOutputDir: Path,
    @ConfigProperty(name = "app.subtitle.mfa.tmp-dir") private val mfaTmpDir: Path,
    @ConfigProperty(name = "app.media-editor.ffmpeg.tmp-dir") private val ffmpegTmpDir: Path,
) {
    fun onStart(@Observes event: StartupEvent) {
        this.mediaInputVideoDir.createDirectories()
        this.mediaInputSilencesDir.createDirectories()
        this.mediaTmpDir.createDirectories()
        this.mediaOutputDir.createDirectories()
        this.mfaTmpDir.createDirectories()
        this.ffmpegTmpDir.createDirectories()
    }
}