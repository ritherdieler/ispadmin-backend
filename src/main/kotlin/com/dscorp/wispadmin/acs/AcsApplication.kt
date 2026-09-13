package com.dscorp.wispadmin.acs

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
@ComponentScan(basePackages = ["com.dscorp.wispadmin.acs", "com.dscorp.wispadmin.events", "com.dscorp.wispadmin.transport"])
@EntityScan(basePackages = ["com.dscorp.wispadmin.acs"])
@EnableJpaRepositories(basePackages = ["com.dscorp.wispadmin.acs"])
@EnableScheduling
@EnableTransactionManagement
class AcsApplication : SpringBootServletInitializer() {
    override fun configure(application: SpringApplicationBuilder): SpringApplicationBuilder {
        return application.sources(AcsApplication::class.java).profiles("acs")
    }
}

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone(System.getProperty("app.timezone", "America/Lima")))
    val builder = SpringApplicationBuilder(AcsApplication::class.java)
    if (!localPrestagingRequested(args)) {
        builder.profiles("acs")
    }
    builder.run(*args)
}

private fun localPrestagingRequested(args: Array<String>): Boolean {
    if (args.any { it.contains("local-prestaging") }) {
        return true
    }
    return System.getenv("SPRING_PROFILES_ACTIVE")?.contains("local-prestaging") == true
}
