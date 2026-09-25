package com.dscorp.wispadmin.acs.genieacs

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.security.MessageDigest

/** Explicitly publishes immutable-by-name v2 provisions; never participates in the legacy bootstrap. */
@Component
@ConditionalOnProperty(prefix = "genieacs", name = ["v2-publish-enabled"], havingValue = "true")
class OnboardingV2GenieAcsProvisionPublisher(
    private val client: GenieAcsClient,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        IDS.forEach { id ->
            val script = requireNotNull(load(id)) { "Missing v2 provision $id" }
            val result = client.putProvision(id, script)
            check(result.accepted) { "GenieACS rejected v2 provision $id" }
            log.info("Published immutable v2 provision id={} sha256={}", id, sha256(script))
        }
    }

    private fun load(id: String): String? = javaClass.getResourceAsStream("/genieacs/provisions/$id.js")
        ?.bufferedReader()?.use { it.readText() }
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private companion object {
        val log = LoggerFactory.getLogger(OnboardingV2GenieAcsProvisionPublisher::class.java)
        val IDS = listOf("gf-onboarding-v2-pppoe", "gf-onboarding-v2-wifi", "gf-onboarding-v2-compensate")
    }
}
