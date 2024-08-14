package com.hoseus.configuration

import io.vertx.mutiny.core.Vertx
import io.vertx.mutiny.core.file.FileSystem
import io.vertx.mutiny.ext.web.client.WebClient
import jakarta.enterprise.context.ApplicationScoped

class BeanProducer {
    @ApplicationScoped
    fun restClient(vertx: Vertx): WebClient = WebClient.create(vertx)

    @ApplicationScoped
    fun fileSystem(vertx: Vertx): FileSystem = vertx.fileSystem()
}