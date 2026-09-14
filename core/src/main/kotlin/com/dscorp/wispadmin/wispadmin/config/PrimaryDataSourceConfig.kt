package com.dscorp.wispadmin.wispadmin.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

@Configuration
class PrimaryDataSourceConfig {

    @Bean(name = ["dataSource"])
    @Primary
    fun dataSource(properties: DataSourceProperties): DataSource =
        properties.initializeDataSourceBuilder().type(HikariDataSource::class.java).build()
}
