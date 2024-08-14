package com.hoseus.subtitle

import io.quarkus.arc.profile.IfBuildProfile
import io.quarkus.arc.properties.IfBuildProperty
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

@ApplicationScoped
@IfBuildProperty(name = "app.subtitle.implementation", stringValue = "mfa", enableIfMissing = false)
@IfBuildProfile(anyOf = ["dev", "test"])
class DockerizedMfaCommandBuilder(
    @ConfigProperty(name = "app.subtitle.batch.size") private val batchSize: Int,
    @ConfigProperty(name = "app.subtitle.mfa.beam") private val beam: Int
) : MfaCommandBuilder {
    private val modelsCacheVolume: String = "mfa-models-cache"
    private val containerCorpusDir: Path = Paths.get("/data")

    override fun buildAlignCommand(corpusDir: Path): List<String> {
        return listOfNotNull(
            "docker",
            "run",
            "--rm",
            "-v", "${this.modelsCacheVolume}:/mfa/pretrained_models",
            "-v", "${corpusDir.absolutePathString()}:${this.containerCorpusDir.absolutePathString()}",
            "mmcauliffe/montreal-forced-aligner:latest",
            "mfa",
            "align",
            "--single_speaker",
            "--use_mp",
            "--num_jobs", this.batchSize.toString(),
            "--include_original_text",
            "--fine_tune",
            "--output_format", "json",
            this.containerCorpusDir.absolutePathString(),
            "/mfa/pretrained_models/dictionary/english_us_arpa.dict",
            "/mfa/pretrained_models/acoustic/english_us_arpa.zip",
            this.containerCorpusDir.absolutePathString(),
            "--beam", this.beam.toString()
        )
    }

    companion object DockerizedMfaCommandBuilder {
        private val logger = Logger.getLogger(DockerizedMfaCommandBuilder::class.java.name)
    }
}