package com.dscorp.wispadmin.traffic.config

import com.dscorp.wispadmin.traffic.service.SchemaColumnDelete
import com.dscorp.wispadmin.traffic.service.TrafficSubscriptionPurgeService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

@Configuration
class TrafficSubscriptionPurgeConfig {
    @Bean
    fun trafficSubscriptionPurgeService(
        @Qualifier("trafficDataSource") dataSource: DataSource,
    ): TrafficSubscriptionPurgeService {
        val jdbc = JdbcTemplate(dataSource)
        val columns = SchemaColumnDelete(jdbc)
        return TrafficSubscriptionPurgeService(
            deleteBySubscriptionId = { id -> columns.delete("subscription_id", id.toString()) },
            deleteByClientIp = { ip -> columns.delete("client_ip", ip) },
        )
    }
}
