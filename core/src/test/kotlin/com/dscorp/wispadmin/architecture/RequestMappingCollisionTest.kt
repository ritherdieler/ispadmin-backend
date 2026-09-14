package com.dscorp.wispadmin.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class RequestMappingCollisionTest {

    private val root = Path.of(System.getProperty("user.dir"))

    @Test
    fun trafficLegacySubscriptionMappingStaysOptIn() {
        val src = Files.readString(
            root.resolve("traffic/src/main/kotlin/com/dscorp/wispadmin/traffic/controller/SubscriptionTrafficController.kt"),
        )
        assertTrue(src.contains("@RequestMapping(\"/subscription\")"), src)
        assertTrue(src.contains("legacy-context-paths"), src)
        assertTrue(src.contains("matchIfMissing = false"), src)
    }

    @Test
    fun trafficPublicPathsStayOnCoreFacadesInTheSingleWar() {
        listOf(
            "traffic/src/main/kotlin/com/dscorp/wispadmin/traffic/controller/NetworkTrafficAnalyticsController.kt",
            "traffic/src/main/kotlin/com/dscorp/wispadmin/traffic/controller/BandwidthIntelligenceController.kt",
        ).forEach { rel ->
            val src = Files.readString(root.resolve(rel))
            assertTrue(src.contains("legacy-context-paths"), "$rel must not collide with core facades: $src")
            assertTrue(src.contains("matchIfMissing = false"), src)
        }
    }

    @Test
    fun satelliteSecurityChainsArePrefixed() {
        val acs = Files.readString(
            root.resolve("acs/src/main/kotlin/com/dscorp/wispadmin/acs/config/AcsConfig.kt"),
        )
        val gateway = Files.readString(
            root.resolve("oltgateway/src/main/kotlin/com/dscorp/wispadmin/oltgateway/config/OltGatewaySecurityConfig.kt"),
        )
        val traffic = Files.readString(
            root.resolve("traffic/src/main/kotlin/com/dscorp/wispadmin/traffic/config/TrafficSecurityConfig.kt"),
        )
        assertTrue(acs.contains("antMatcher(\"/api/acs/v1/**\")"), acs)
        assertTrue(gateway.contains("antMatcher(\"/api/olt-gateway/**\")"), gateway)
        assertTrue(traffic.contains("\"/api/traffic/v1/**\""), traffic)
        assertTrue(traffic.contains("\"/traffic/**\""), traffic)
    }

    @Test
    fun subscriptionTrafficStompMappingsAreMutuallyExclusive() {
        val relay = Files.readString(
            root.resolve("core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/trafficclient/CoreTrafficStreamRelay.kt"),
        )
        val trafficWs = Files.readString(
            root.resolve("traffic/src/main/kotlin/com/dscorp/wispadmin/traffic/websocket/SubscriptionTrafficWebSocket.kt"),
        )
        assertTrue(relay.contains("@MessageMapping(\"/subscription-traffic/start\")"), relay)
        assertTrue(trafficWs.contains("@MessageMapping(\"/subscription-traffic/start\")"), trafficWs)
        assertTrue(
            Regex("""havingValue\s*=\s*"true"""").containsMatchIn(relay),
            "CoreTrafficStreamRelay must load only when traffic.client-enabled=true: $relay",
        )
        assertTrue(trafficWs.contains("@ConditionalOnProperty"), trafficWs)
        assertTrue(
            Regex("""havingValue\s*=\s*"false"""").containsMatchIn(trafficWs),
            "SubscriptionTrafficWebSocket must stay off when the core relay is the facade: $trafficWs",
        )
    }

    @Test
    fun satelliteApplicationsDoNotRescanTheirPackage() {
        listOf(
            "acs/src/main/kotlin/com/dscorp/wispadmin/acs/AcsApplication.kt",
            "oltgateway/src/main/kotlin/com/dscorp/wispadmin/oltgateway/OltGatewayApplication.kt",
            "traffic/src/main/kotlin/com/dscorp/wispadmin/traffic/TrafficApplication.kt",
        ).forEach { rel ->
            val text = Files.readString(root.resolve(rel))
            assertFalse(
                text.contains("@ComponentScan"),
                "$rel nested ComponentScan double-registers controllers in the single WAR: $text",
            )
            assertTrue(text.contains("@EnableScheduling"), "$rel must keep scheduling: $text")
        }
    }
}
