package com.hoseus.cli

import com.hoseus.exception.CommandLineExecutionFailedException
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger

@ApplicationScoped
class CommandLineExecutor {
    fun executeCommand(command: List<String>): Uni<String> {
        return this.executeCommand(*command.toTypedArray())
    }

    fun executeCommand(vararg command: String) : Uni<String> {
        return Uni
            .createFrom()
            .completionStage {
                ProcessBuilder(*command)
                    .start()
                    .onExit()
            }
            .flatMap {
                if(it.exitValue() != 0) {
                    val errorOutput = it.errorStream.bufferedReader().use { reader -> reader.readText() }
                    Uni.createFrom().failure(CommandLineExecutionFailedException(command = command.joinToString(" "), exitValue = it.exitValue(), errorOutput = errorOutput))
                } else {
                    Uni.createFrom().item(it.inputStream.bufferedReader().use { reader -> reader.readText() })
                }
            }
    }

    companion object CommandLineExecutor {
        private val logger = Logger.getLogger(CommandLineExecutor::class.java)
    }
}