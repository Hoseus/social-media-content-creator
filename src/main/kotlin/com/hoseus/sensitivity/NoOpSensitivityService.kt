package com.hoseus.sensitivity

import io.quarkus.arc.properties.IfBuildProperty
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@IfBuildProperty(name = "app.sensitivity.implementation", stringValue = "no-op", enableIfMissing = false)
class NoOpSensitivityService : SensitivityService {
    override fun obfuscateSensitiveWords(text: String): Uni<String> {
        return Uni.createFrom().item(text)
    }
}