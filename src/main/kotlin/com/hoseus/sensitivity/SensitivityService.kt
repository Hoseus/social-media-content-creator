package com.hoseus.sensitivity

import io.smallrye.mutiny.Uni

fun interface SensitivityService{
    fun obfuscateSensitiveWords(text: String): Uni<String>
}