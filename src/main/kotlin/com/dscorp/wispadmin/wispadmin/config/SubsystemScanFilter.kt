package com.dscorp.wispadmin.wispadmin.config

import org.springframework.core.env.Environment
import org.springframework.core.type.classreading.MetadataReader
import org.springframework.core.type.classreading.MetadataReaderFactory
import org.springframework.core.type.filter.TypeFilter
import org.springframework.context.EnvironmentAware

class SubsystemScanFilter : TypeFilter, EnvironmentAware {

    private lateinit var environment: Environment

    override fun setEnvironment(environment: Environment) {
        this.environment = environment
    }

    override fun match(metadataReader: MetadataReader, metadataReaderFactory: MetadataReaderFactory): Boolean {
        val className = metadataReader.classMetadata.className
        val key = keyFor(className) ?: return false
        val enabled = environment.getProperty(
            "gigafiber.subsystems.$key.enabled",
            Boolean::class.java,
            true
        )
        return !enabled
    }

    companion object {
        val PACKAGE_KEYS: Map<String, String> = linkedMapOf(
            "com.dscorp.wispadmin.observability" to "observability",
            "com.dscorp.wispadmin.oltgateway" to "oltgateway",
            "com.dscorp.wispadmin.netdiag" to "netdiag",
            "com.dscorp.wispadmin.traffic" to "traffic",
            "com.dscorp.wispadmin.servicehealth" to "servicehealth"
        )

        fun keyFor(className: String): String? {
            return PACKAGE_KEYS.entries
                .firstOrNull { className == it.key || className.startsWith(it.key + ".") }
                ?.value
        }

        fun entityPackages(environment: Environment): Array<String> {
            val enabled = PACKAGE_KEYS.entries
                .filter { (_, key) ->
                    environment.getProperty(
                        "gigafiber.subsystems.$key.enabled",
                        Boolean::class.java,
                        true
                    )
                }
                .map { it.key }
            return (listOf("com.dscorp.wispadmin.wispadmin") + enabled).toTypedArray()
        }
    }
}
