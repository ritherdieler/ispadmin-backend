package com.dscorp.wispadmin.wispadmin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody


@SpringBootApplication
@EnableScheduling
@EnableAspectJAutoProxy
class WispAdminApplication
    : SpringBootServletInitializer()
{
    override fun configure(application: SpringApplicationBuilder): SpringApplicationBuilder {
        return application.sources(WispAdminApplication::class.java)
    }
}

fun main(args: Array<String>) {
    runApplication<WispAdminApplication>(*args)
}
