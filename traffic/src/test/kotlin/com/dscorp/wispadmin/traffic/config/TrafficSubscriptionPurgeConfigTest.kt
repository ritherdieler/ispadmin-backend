package com.dscorp.wispadmin.traffic.config

import com.dscorp.wispadmin.traffic.service.TrafficSubscriptionPurgeService
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jdbc.core.JdbcTemplate

class TrafficSubscriptionPurgeConfigTest {

    @Test
    fun `purge uses traffic datasource and leaves primary core data untouched`() {
        val core = database("core_purge_test")
        val traffic = database("traffic_purge_test")
        val context = AnnotationConfigApplicationContext()
        context.beanFactory.registerSingleton("dataSource", core)
        context.beanFactory.registerSingleton("trafficDataSource", traffic)
        context.beanFactory.registerSingleton("jdbcTemplate", JdbcTemplate(core))
        context.register(TrafficSubscriptionPurgeConfig::class.java)

        try {
            context.refresh()
            context.getBean(TrafficSubscriptionPurgeService::class.java)
                .purge(subscriptionId = 118, ip = "192.168.250.10", pppoeUsername = null)

            assertEquals(1, count(core), "Core-owned rows must not be touched by the traffic purge")
            assertEquals(0, count(traffic), "The matching traffic history row must be removed")
        } finally {
            context.close()
            core.connection.use { it.createStatement().execute("SHUTDOWN") }
            traffic.connection.use { it.createStatement().execute("SHUTDOWN") }
        }
    }

    private fun database(name: String): JdbcDataSource {
        val url = "jdbc:h2:mem:$name;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        val dataSource = JdbcDataSource().apply { setURL(url) }
        JdbcTemplate(dataSource).execute("CREATE SCHEMA $name")
        dataSource.setURL("$url;SCHEMA=$name")
        JdbcTemplate(dataSource).apply {
            execute("CREATE TABLE traffic_sample (subscription_id INT, client_ip VARCHAR(64))")
            update("INSERT INTO traffic_sample (subscription_id, client_ip) VALUES (?, ?)", 118, "192.168.250.10")
        }
        return dataSource
    }

    private fun count(dataSource: JdbcDataSource): Int =
        requireNotNull(JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM traffic_sample", Int::class.java))
}
