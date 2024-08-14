package com.hoseus.tts

import io.quarkus.arc.profile.IfBuildProfile
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

@ApplicationScoped
@IfBuildProfile(anyOf = ["dev", "test"])
class DockerizedCoquiTtsCommandBuilder(
    @ConfigProperty(name = "app.tts.coqui-tts.model-name") private val modelName: String,
    @ConfigProperty(name = "app.tts.coqui-tts.female-speaker-idx") private val femaleSpeakerIdx: String,
    @ConfigProperty(name = "app.tts.coqui-tts.male-speaker-idx") private val maleSpeakerIdx: String,
    @ConfigProperty(name = "app.tts.coqui-tts.use-cuda") private val useCuda: Boolean
) : CoquiTtsCommandBuilder {
    private val modelsCacheVolume: String = "coqui-tts-models-cache"
    private val containerResultDir = Paths.get("/root", "tts-output")

    override fun buildSynthesizeSpeechCommand(text: String, speakerSex: SpeakerSex, resultFile: Path): List<String> {
        val containerResultFile = Paths.get(containerResultDir.absolutePathString(), resultFile.name)

        return listOfNotNull(
            "docker",
            "run",
            "--rm",
            "-e", "COQUI_TOS_AGREED=1",
            "-v", "${this.modelsCacheVolume}:/root/.local/share/tts",
            "-v", "${resultFile.toAbsolutePath().parent.absolutePathString()}:${containerResultFile.toAbsolutePath().parent.absolutePathString()}",
            "--gpus", "all",
            "ghcr.io/coqui-ai/tts:latest",
            "--model_name", this.modelName,
            "--speaker_idx", if(speakerSex == SpeakerSex.F) this.femaleSpeakerIdx else this.maleSpeakerIdx,
            "--language_idx", "en",
            "--text", "\"$text\"",
            "--out_path", containerResultFile.absolutePathString(),
            "--use_cuda", this.useCuda.toString()
        )
    }

    companion object DockerizedCoquiTtsCommandBuilder {
        private val logger = Logger.getLogger(DockerizedCoquiTtsCommandBuilder::class.java.name)
    }
}