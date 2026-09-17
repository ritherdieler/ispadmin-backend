package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ProdEnvironmentPropertiesTest {

    private val root = repoRoot()

    private val prod: String by lazy {
        Files.readString(root.resolve("core/src/main/resources/application-prod.properties"))
    }

    private val staging: String by lazy {
        Files.readString(root.resolve("core/src/main/resources/application-staging.properties"))
    }

    @Test
    fun prodKeepsFlywayOnAndHibernateValidate() {
        assertTrue(
            Regex("""^spring\.jpa\.hibernate\.ddl-auto=validate\s*$""", RegexOption.MULTILINE).containsMatchIn(prod),
            prod,
        )
        assertTrue(
            Regex("""^spring\.flyway\.enabled=true\s*$""", RegexOption.MULTILINE).containsMatchIn(prod),
            prod,
        )
        assertTrue(
            Regex("""^spring\.flyway\.baseline-on-migrate=true\s*$""", RegexOption.MULTILINE).containsMatchIn(prod),
            prod,
        )
        assertTrue(
            Regex("""^spring\.flyway\.baseline-version=38\s*$""", RegexOption.MULTILINE).containsMatchIn(prod),
            prod,
        )
        assertFalse(
            Regex("""^spring\.jpa\.hibernate\.ddl-auto=update\s*$""", RegexOption.MULTILINE).containsMatchIn(prod),
            "prod overlay must not copy staging ddl-auto=update",
        )
    }

    @Test
    fun prodKeepsNewPppoeSubscriptionsOff() {
        assertTrue(
            Regex(
                """^pppoe\.new-subscriptions\.enabled=\$\{PPPOE_NEW_SUBSCRIPTIONS_ENABLED:false\}\s*$""",
                RegexOption.MULTILINE,
            ).containsMatchIn(prod),
            prod,
        )
        assertFalse(prod.contains("pppoe.new-subscriptions.enabled=true"), prod)
        assertFalse(prod.contains("PPPOE_NEW_SUBSCRIPTIONS_ENABLED:true"), prod)
    }

    @Test
    fun prodEnvironmentTagIsNotStaging() {
        assertFalse(prod.contains("gigafiber.environment.tag=stg"), prod)
        assertFalse(prod.contains("gigafiber.environment.tag=lpstg"), prod)
        assertFalse(
            Regex("""^gigafiber\.environment\.tag=""", RegexOption.MULTILINE).containsMatchIn(prod),
            "prod tag must stay unset so queues/VLAN/360 do not follow stg rules: $prod",
        )
        assertTrue(
            prod.contains("gigafiber.environment.tag stays unset"),
            "prod overlay must say explicitly that the tag stays empty",
        )
    }

    @Test
    fun prodKeepsTrafficPollEnabled() {
        assertTrue(
            Regex("""^traffic\.poll\.enabled=true\s*$""", RegexOption.MULTILINE).containsMatchIn(prod),
            "do not turn traffic.poll.enabled off in the prod overlay: $prod",
        )
        assertTrue(prod.contains("gigafiber.redis.namespace=prod"), prod)
    }

    @Test
    fun prodOverlayIsNotACopyOfStaging() {
        assertTrue(staging.contains("gigafiber.environment.tag=stg"), staging)
        assertTrue(staging.contains("spring.jpa.hibernate.ddl-auto=update"), staging)
        assertFalse(prod.contains("server.servlet.context-path=/ispadmin-staging"), prod)
        assertFalse(prod.contains("jdbc:mysql://mysql:3306/ispadmin_staging"), prod)
        assertFalse(prod.contains("jdbc:mysql://mysql:3306/stg_acs"), prod)
        assertTrue(prod.contains("jdbc:mysql://mysql:3306/prod_acs"), prod)
        assertTrue(prod.contains("jdbc:mysql://mysql:3306/prod_oltgateway"), prod)
        assertTrue(prod.contains("jdbc:mysql://mysql:3306/prod_traffic"), prod)
    }

    private fun repoRoot(): Path {
        var dir = Path.of(System.getProperty("user.dir"))
        repeat(6) {
            if (Files.exists(dir.resolve("core/src/main/resources/application-prod.properties"))) {
                return dir
            }
            dir = dir.parent ?: return Path.of(System.getProperty("user.dir"))
        }
        return Path.of(System.getProperty("user.dir"))
    }
}
