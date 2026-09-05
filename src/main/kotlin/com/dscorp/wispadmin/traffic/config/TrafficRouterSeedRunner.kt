package com.dscorp.wispadmin.traffic.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class TrafficRouterSeedRunner(
    private val jdbcTemplate: JdbcTemplate,
    private val properties: TrafficProperties,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (!properties.routerSeed.enabled) return
        val source = properties.routerSeed.sourceSchema.trim()
        val deviceTable = if (source.isEmpty()) "network_device" else "$source.network_device"
        if (!tableExists(if (source.isEmpty()) "network_device" else null, source)) return
        val inserted = jdbcTemplate.update(
            """
            INSERT INTO traffic_router (id, name, host, username, password, enabled)
            SELECT nd.id,
                   COALESCE(nd.name, CONCAT('router-', nd.id)),
                   nd.ip_address,
                   COALESCE(nd.username, ''),
                   COALESCE(nd.password, ''),
                   IF(nd.disabled = 1, 0, 1)
            FROM $deviceTable nd
            WHERE nd.network_device_type IN ('FIBER_ROUTER', 'CLOUD_CORE_ROUTER', 'WIRELESS_ROUTER')
              AND nd.ip_address IS NOT NULL AND TRIM(nd.ip_address) <> ''
            ON DUPLICATE KEY UPDATE
              name = VALUES(name),
              host = VALUES(host),
              username = VALUES(username),
              password = VALUES(password),
              enabled = VALUES(enabled)
            """.trimIndent()
        )
        if (inserted > 0) {
            log.info("Traffic router seed upserted {} rows from {}", inserted, deviceTable)
        }
    }

    private fun tableExists(unqualified: String?, schema: String): Boolean {
        val sql = if (schema.isEmpty()) {
            """
            SELECT COUNT(*)
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'network_device'
            """.trimIndent()
        } else {
            """
            SELECT COUNT(*)
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'network_device'
            """.trimIndent()
        }
        val count = if (schema.isEmpty()) {
            jdbcTemplate.queryForObject(sql, Int::class.java)
        } else {
            jdbcTemplate.queryForObject(sql, Int::class.java, schema)
        }
        return count != 0
    }

    private companion object {
        val log = LoggerFactory.getLogger(TrafficRouterSeedRunner::class.java)
    }
}
