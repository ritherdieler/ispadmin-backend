package com.dscorp.wispadmin.wispadmin.config
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
class RedisEnvironmentIsolationTest {
    @Test fun `production and staging configure distinct Redis namespaces`() {
        fun props(name: String)=Properties().apply { Files.newInputStream(Path.of("src/main/resources/application-$name.properties")).use { load(it) } }
        assertEquals("prod",props("prod").getProperty("gigafiber.redis.namespace"))
        assertEquals("stg",props("staging").getProperty("gigafiber.redis.namespace"))
        val pom=Files.readString(Path.of("pom.xml"))
        for (profile in listOf("traffic-staging-war","oltgateway-staging-war","acs-staging-war")) {
            assertTrue(pom.substringAfter("<id>$profile</id>").substringBefore("</profile>").contains("gigafiber.redis.namespace=stg"),profile)
        }
    }
}
