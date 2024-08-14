package com.hoseus.util

import java.io.InputStream

class ResourceLoader {
    companion object {
        fun loadResource(resource: String): InputStream? {
            return ResourceLoader::class.java.classLoader.getResourceAsStream(resource)
        }
    }
}