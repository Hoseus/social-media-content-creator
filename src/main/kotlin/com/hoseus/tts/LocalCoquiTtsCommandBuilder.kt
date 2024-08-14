package com.hoseus.tts

import io.quarkus.arc.profile.IfBuildProfile
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@ApplicationScoped
@IfBuildProfile(value = "prod")
class LocalCoquiTtsCommandBuilder(
    @ConfigProperty(name = "app.tts.coqui-tts.model-name") private val modelName: String,
    @ConfigProperty(name = "app.tts.coqui-tts.female-speaker-idx") private val femaleSpeakerIdx: String,
    @ConfigProperty(name = "app.tts.coqui-tts.male-speaker-idx") private val maleSpeakerIdx: String,
    @ConfigProperty(name = "app.tts.coqui-tts.use-cuda") private val useCuda: Boolean
) : CoquiTtsCommandBuilder {
    override fun buildSynthesizeSpeechCommand(text: String, speakerSex: SpeakerSex, resultFile: Path): List<String> {

        return listOfNotNull(
            "tts",
            "--model_name", this.modelName,
            "--speaker_idx", if(speakerSex == SpeakerSex.F) this.femaleSpeakerIdx else this.maleSpeakerIdx,
            "--language_idx", "en",
            "--text", "\"$text\"",
            "--out_path", resultFile.absolutePathString(),
            if(this.useCuda) "--use_cuda" else null
        )
    }

    companion object LocalCoquiTtsCommandBuilder {
        private val logger = Logger.getLogger(LocalCoquiTtsCommandBuilder::class.java.name)
    }
}