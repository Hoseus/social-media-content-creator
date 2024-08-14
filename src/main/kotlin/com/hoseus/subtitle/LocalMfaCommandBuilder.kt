package com.hoseus.subtitle

import io.quarkus.arc.profile.IfBuildProfile
import io.quarkus.arc.properties.IfBuildProperty
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@ApplicationScoped
@IfBuildProperty(name = "app.subtitle.implementation", stringValue = "mfa", enableIfMissing = false)
@IfBuildProfile(value = "prod")
class LocalMfaCommandBuilder(
    @ConfigProperty(name = "app.subtitle.batch.size") private val batchSize: Int,
    @ConfigProperty(name = "app.subtitle.mfa.beam") private val beam: Int,
) : MfaCommandBuilder {
    override fun buildAlignCommand(corpusDir: Path): List<String> {
        return listOfNotNull(
            "mfa",
            "align",
            "--single_speaker",
            "--use_mp",
            "--num_jobs", this.batchSize.toString(),
            "--include_original_text",
            "--fine_tune",
            "--output_format", "json",
            corpusDir.absolutePathString(),
            "/mfa/pretrained_models/dictionary/english_us_arpa.dict",
            "/mfa/pretrained_models/acoustic/english_us_arpa.zip",
            corpusDir.absolutePathString(),
            "--beam", this.beam.toString()
        )
    }

    companion object LocalMfaCommandBuilder {
        private val logger = Logger.getLogger(LocalMfaCommandBuilder::class.java.name)
    }
}