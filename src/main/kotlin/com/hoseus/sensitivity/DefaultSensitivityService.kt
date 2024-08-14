package com.hoseus.sensitivity

import io.quarkus.arc.DefaultBean
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@DefaultBean
class DefaultSensitivityService(
    @ConfigProperty(name = "app.sensitivity.mappings") private val wordMappings: Map<String, String>,
) : SensitivityService {
    override fun obfuscateSensitiveWords(text: String): Uni<String> {
        return Uni.createFrom().item {
            var modifiedText = text
            for ((word, replacement) in this.wordMappings) {
                modifiedText = modifiedText.replace(
                    regex = "(^|\\s+)$word($|\\s+)".toRegex(RegexOption.IGNORE_CASE),
                    transform = {
                        val capturedGroups = it.groupValues
                        "${capturedGroups[1]}${replacement}${capturedGroups[2]}"
                    }
                )
            }
            modifiedText
        }
    }
}