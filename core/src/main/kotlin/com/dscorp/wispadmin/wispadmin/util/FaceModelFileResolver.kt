package com.dscorp.wispadmin.wispadmin.util

import org.springframework.stereotype.Component
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

@Component
class FaceModelFileResolver {
    private val resolvedFiles = ConcurrentHashMap<String, File>()

    fun resolve(configuredPath: String, modelLabel: String): File {
        resolvedFiles[configuredPath]?.let { cached ->
            if (cached.exists() && cached.isFile) return cached
            resolvedFiles.remove(configuredPath)
        }

        val resolved = when {
            configuredPath.startsWith(CLASSPATH_PREFIX) -> resolveClasspathModel(
                resourcePath = configuredPath.removePrefix(CLASSPATH_PREFIX),
                modelLabel = modelLabel,
                configuredPath = configuredPath
            )
            else -> resolveFilesystemModel(configuredPath, modelLabel)
        }

        resolvedFiles[configuredPath] = resolved
        return resolved
    }

    private fun resolveFilesystemModel(configuredPath: String, modelLabel: String): File {
        val modelFile = File(configuredPath)
        if (modelFile.exists() && modelFile.isFile) return modelFile

        throw IllegalStateException(
            "No se encontro el modelo facial DJL ($modelLabel) en $configuredPath."
        )
    }

    private fun resolveClasspathModel(
        resourcePath: String,
        modelLabel: String,
        configuredPath: String
    ): File {
        val normalizedResourcePath = resourcePath.removePrefix("/")
        val inputStream = openClasspathStream(normalizedResourcePath)
            ?: throw IllegalStateException(
                "No se encontro el modelo facial DJL ($modelLabel) en classpath:$normalizedResourcePath."
            )

        val suffix = normalizedResourcePath.substringAfterLast('.', "zip")
        val tempFile = Files.createTempFile("djl-$modelLabel-", ".$suffix").toFile()
        tempFile.deleteOnExit()

        inputStream.use { source ->
            Files.copy(source, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        if (!tempFile.exists() || tempFile.length() <= 0L) {
            throw IllegalStateException(
                "No se pudo extraer el modelo facial DJL ($modelLabel) desde $configuredPath."
            )
        }

        return tempFile
    }

    private fun openClasspathStream(resourcePath: String): InputStream? {
        return Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            ?: FaceModelFileResolver::class.java.classLoader.getResourceAsStream(resourcePath)
    }

    companion object {
        private const val CLASSPATH_PREFIX = "classpath:"
    }
}
