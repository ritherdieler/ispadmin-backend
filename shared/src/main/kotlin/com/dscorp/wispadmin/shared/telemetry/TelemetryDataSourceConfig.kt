package com.dscorp.wispadmin.shared.telemetry

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import javax.sql.DataSource

@ConfigurationProperties(prefix = "telemetry.datasource")
class TelemetryDataSourceProperties {
    var url: String = ""
    var username: String = ""
    var password: String = ""
}

@Configuration
@EnableConfigurationProperties(TelemetryDataSourceProperties::class)
class TelemetryDataSourceConfig {

    @Bean(name = ["telemetryDataSource"])
    @ConditionalOnExpression("'\${telemetry.datasource.url:}'.trim().length() > 0")
    fun dedicatedTelemetryDataSource(properties: TelemetryDataSourceProperties): DataSource =
        DataSourceBuilder.create()
            .type(HikariDataSource::class.java)
            .url(properties.url)
            .username(properties.username)
            .password(properties.password)
            .build()
}

@Component
class TelemetryDataSourceHealth(
    private val properties: TelemetryDataSourceProperties,
    @Qualifier("dataSource") private val primary: DataSource,
) {
    fun usesDedicatedSchema(): Boolean = properties.url.isNotBlank()
}
