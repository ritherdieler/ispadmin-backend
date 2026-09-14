package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.util.AppTimeZone
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct

@Configuration
class AppTimeZoneConfiguration(
    @Value("\${app.timezone:America/Lima}") private val timezone: String
) {
    @PostConstruct
    fun init() {
        AppTimeZone.initialize(timezone)
    }
}
