package com.dscorp.wispadmin.oltgateway

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
@ComponentScan(basePackages = ["com.dscorp.wispadmin.oltgateway", "com.dscorp.wispadmin.events"])
@EntityScan(basePackages = ["com.dscorp.wispadmin.oltgateway"])
@EnableJpaRepositories(basePackages = ["com.dscorp.wispadmin.oltgateway"])
@EnableScheduling
@EnableTransactionManagement
class OltGatewayApplication : SpringBootServletInitializer() {
    override fun configure(application: SpringApplicationBuilder): SpringApplicationBuilder {
        return application.sources(OltGatewayApplication::class.java).profiles("prod", "oltgateway")
    }
}

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone(System.getProperty("app.timezone", "America/Lima")))
    SpringApplicationBuilder(OltGatewayApplication::class.java).profiles("prod", "oltgateway").run(*args)
}
