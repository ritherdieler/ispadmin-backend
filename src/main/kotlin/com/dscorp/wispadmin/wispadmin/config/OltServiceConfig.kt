package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.service.MockOltService
import com.dscorp.wispadmin.wispadmin.service.OltService
import com.dscorp.wispadmin.wispadmin.service.RealOltService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class OltServiceConfig {

    @Value("\${olt.service.mock.enabled:false}")
    private lateinit var mockEnabled: String

    @Bean
    @Primary
    fun oltService(
        realOltService: RealOltService,
        mockOltService: MockOltService
    ): OltService {
        return if (mockEnabled.toBoolean()) {
            mockOltService
        } else {
            realOltService
        }
    }
}
