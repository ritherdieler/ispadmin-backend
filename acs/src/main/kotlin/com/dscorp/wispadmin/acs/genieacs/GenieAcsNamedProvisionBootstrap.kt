package com.dscorp.wispadmin.acs.genieacs

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "genieacs", name = ["enabled"], havingValue = "true")
class GenieAcsNamedProvisionBootstrap(
    private val client: GenieAcsClient,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(GenieAcsNamedProvisionBootstrap::class.java)

    override fun run(args: ApplicationArguments) {
        NamedGenieAcsProvisions.ids.forEach { id ->
            val script = loadScript(id)
            if (script == null) {
                log.warn("Named provision {} missing on classpath", id)
                return@forEach
            }
            val result = try {
                client.putProvision(id, script)
            } catch (ex: Exception) {
                log.warn("PUT provision {} failed: {}", id, ex.message)
                return@forEach
            }
            if (!result.accepted) {
                log.warn("PUT provision {} HTTP {}", id, result.statusCode)
            }
        }
    }

    internal fun loadScript(id: String): String? {
        val stream = javaClass.getResourceAsStream(NamedGenieAcsProvisions.classpathPath(id)) ?: return null
        return stream.bufferedReader().use { it.readText() }
    }
}
