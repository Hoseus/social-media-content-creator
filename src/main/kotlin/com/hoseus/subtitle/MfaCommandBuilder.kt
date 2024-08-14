package com.hoseus.subtitle

import java.nio.file.Path

interface MfaCommandBuilder {
    fun buildAlignCommand(corpusDir: Path): List<String>
}