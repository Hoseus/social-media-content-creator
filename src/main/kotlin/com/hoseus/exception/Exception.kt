package com.hoseus.exception

open class UnexpectedErrorException(
    override val message: String = "Something went wrong!",
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

open class CommandLineExecutionFailedException(
    val description: String = "Error executing command",
    val command: String,
    val exitValue: Int,
    val errorOutput: String,
    override val message: String = "$description - exit value: $exitValue - error log: \n$errorOutput",
    override val cause: Throwable? = null,
) : UnexpectedErrorException(message = message, cause = cause)