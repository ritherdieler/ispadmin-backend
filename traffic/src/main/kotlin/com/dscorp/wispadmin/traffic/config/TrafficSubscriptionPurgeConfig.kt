package com.dscorp.wispadmin.traffic.config

import com.dscorp.wispadmin.traffic.service.SchemaColumnDelete
import com.dscorp.wispadmin.traffic.service.TrafficSubscriptionPurgeService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

@Configuration
class TrafficSubscriptionPurgeConfig {
    @Bean
    fun trafficSubscriptionPurgeService(jdbc: JdbcTemplate) : TrafficSubscriptionPurgeService {
        val columns = SchemaColumnDelete(jdbc)
        return TrafficSubscriptionPurgeService(
            deleteBySubscriptionId = { id -> columns.delete("subscription_id", id.toString()) },
            deleteByClientIp = { ip -> columns.delete("client_ip", ip) },
        )
    }
}
