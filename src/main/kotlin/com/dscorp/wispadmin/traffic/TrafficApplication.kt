package com.dscorp.wispadmin.traffic

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.util.TimeZone

@SpringBootApplication
@ComponentScan(
    basePackages = [
        "com.dscorp.wispadmin.traffic",
        "com.dscorp.wispadmin.events",
        "com.dscorp.wispadmin.routeros",
    ]
)
@EntityScan(basePackages = ["com.dscorp.wispadmin.traffic"])
@EnableJpaRepositories(basePackages = ["com.dscorp.wispadmin.traffic"])
@EnableScheduling
@EnableTransactionManagement
class TrafficApplication : SpringBootServletInitializer() {
    override fun configure(application: SpringApplicationBuilder): SpringApplicationBuilder {
        return application.sources(TrafficApplication::class.java).profiles("traffic")
    }
}

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone(System.getProperty("app.timezone", "America/Lima")))
    SpringApplicationBuilder(TrafficApplication::class.java).profiles("traffic").run(*args)
}
