package com.dscorp.wispadmin.wispadmin

import com.dscorp.wispadmin.wispadmin.config.SubsystemEntityScan
import com.dscorp.wispadmin.wispadmin.config.SubsystemScanFilter
import com.dscorp.wispadmin.wispadmin.util.AppTimeZone
import com.dscorp.wispadmin.wispadmin.util.DjlNativeBootstrap
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.TypeExcludeFilter
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.FilterType
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication
@ComponentScan(
    basePackages = [
        "com.dscorp.wispadmin.wispadmin",
        "com.dscorp.wispadmin.observability",
        "com.dscorp.wispadmin.oltgateway",
        "com.dscorp.wispadmin.routeros",
        "com.dscorp.wispadmin.netdiag",
        "com.dscorp.wispadmin.traffic",
        "com.dscorp.wispadmin.servicehealth",
        "com.dscorp.wispadmin.shared"
    ],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.CUSTOM, classes = [TypeExcludeFilter::class]),
        ComponentScan.Filter(type = FilterType.CUSTOM, classes = [AutoConfigurationExcludeFilter::class]),
        ComponentScan.Filter(type = FilterType.CUSTOM, classes = [SubsystemScanFilter::class])
    ]
)
@SubsystemEntityScan
@EnableJpaRepositories(
    basePackages = [
        "com.dscorp.wispadmin.wispadmin",
        "com.dscorp.wispadmin.observability",
        "com.dscorp.wispadmin.oltgateway",
        "com.dscorp.wispadmin.netdiag",
        "com.dscorp.wispadmin.traffic",
        "com.dscorp.wispadmin.servicehealth"
    ],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.CUSTOM, classes = [SubsystemScanFilter::class])
    ]
)

@EnableAsync
@EnableAspectJAutoProxy
@EnableTransactionManagement
class WispAdminApplication
    : SpringBootServletInitializer()
{
    override fun configure(application: SpringApplicationBuilder): SpringApplicationBuilder {
        DjlNativeBootstrap.initialize()
        return application.sources(WispAdminApplication::class.java)
    }

    companion object {
        init {
            DjlNativeBootstrap.initialize()
        }
    }
}

fun main(args: Array<String>) {
    DjlNativeBootstrap.initialize()
    AppTimeZone.initialize(System.getProperty("app.timezone", "America/Lima"))
    runApplication<WispAdminApplication>(*args)
}
