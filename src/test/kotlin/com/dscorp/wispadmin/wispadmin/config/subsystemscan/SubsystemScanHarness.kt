package com.dscorp.wispadmin.wispadmin.config.subsystemscan

import com.dscorp.wispadmin.wispadmin.config.SubsystemScanFilter
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class, HibernateJpaAutoConfiguration::class])
@ComponentScan(
    basePackages = [
        "com.dscorp.wispadmin.wispadmin.config.subsystemscan",
        "com.dscorp.wispadmin.servicehealth"
    ],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.CUSTOM, classes = [SubsystemScanFilter::class])
    ]
)
class SubsystemScanHarness
