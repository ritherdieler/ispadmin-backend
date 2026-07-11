package com.dscorp.wispadmin.wispadmin

import com.dscorp.wispadmin.wispadmin.util.AppTimeZone
import com.dscorp.wispadmin.wispadmin.util.DjlNativeBootstrap
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication
@EnableScheduling
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
