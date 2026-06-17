package com.dscorp.wispadmin.wispadmin.util

import ai.djl.engine.Engine
import org.slf4j.LoggerFactory

object DjlNativeBootstrap {
    private val logger = LoggerFactory.getLogger(DjlNativeBootstrap::class.java)
    private var initialized = false

    fun initialize() {
        if (initialized) {
            return
        }
        synchronized(this) {
            if (initialized) {
                return
            }
            if (System.getenv("PYTORCH_VERSION").isNullOrBlank() &&
                System.getProperty("PYTORCH_VERSION").isNullOrBlank()
            ) {
                System.setProperty("PYTORCH_VERSION", "2.7.1")
            }
            if (System.getenv("PYTORCH_FLAVOR").isNullOrBlank() &&
                System.getProperty("PYTORCH_FLAVOR").isNullOrBlank()
            ) {
                System.setProperty("PYTORCH_FLAVOR", "cpu")
            }
            logNativeArtifactsOnClasspath()
            initializePytorchEngine()
            initialized = true
        }
    }

    private fun initializePytorchEngine() {
        try {
            val engine = Engine.getEngine("PyTorch")
            logger.info("DJL bootstrap: PyTorch engine initialized: {}", engine.engineName)
        } catch (e: Exception) {
            logger.error(
                "DJL bootstrap: PyTorch engine failed to initialize. " +
                    "Verify CATALINA_HOME/lib has DJL/PyTorch jars, native helper, slf4j-api, gson, jna and commons-compress.",
                e
            )
            throw e
        }
    }

    private fun logNativeArtifactsOnClasspath() {
        val classLoader = Thread.currentThread().contextClassLoader
        val nativePrefix = "pytorch-native-cpu"
        val resources = classLoader.getResources("META-INF/MANIFEST.MF")
        val nativeJars = mutableListOf<String>()
        while (resources.hasMoreElements()) {
            val manifestUrl = resources.nextElement()
            val jarPath = manifestUrl.path.substringBefore("!/")
                .removePrefix("file:")
                .let { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }
            if (jarPath.contains(nativePrefix)) {
                nativeJars.add(jarPath.substringAfterLast('/'))
            }
        }
        val nativeJarList = if (nativeJars.isEmpty()) {
            "NONE (build WAR with -Ddjl.linux or on Linux server)"
        } else {
            nativeJars.joinToString(", ")
        }
        logger.info(
            "DJL bootstrap: os={} arch={} native_helper={} pytorch_native_jars={}",
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
            System.getProperty("ai.djl.pytorch.native_helper"),
            nativeJarList
        )
        if (nativeJars.isEmpty()) {
            logger.error(
                "No pytorch-native-cpu jar found on classpath. " +
                    "Rebuild with: bash mvnw clean package -DskipTests -Ddjl.linux (x86_64) " +
                    "or -Ddjl.linux.aarch64 (ARM Debian)."
            )
        }
        if (!System.getProperty("catalina.base").isNullOrBlank() &&
            (nativeJars.isEmpty() || nativeJars.any { it.contains("osx-") })
        ) {
            logger.error(
                "External Tomcat requires Linux DJL natives in the WAR and DJL jars in CATALINA_HOME/lib. " +
                    "For the Debian x86_64 VPS, rebuild with: bash mvnw clean package -DskipTests -Ddjl.linux; " +
                    "run: bash scripts/verify-djl-war.sh; then copy target/tomcat-lib/*.jar to CATALINA_HOME/lib, " +
                    "including ispadmin-djl-native-helper.jar."
            )
        }
    }
}
