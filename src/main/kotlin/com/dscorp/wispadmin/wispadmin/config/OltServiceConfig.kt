package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.service.MockOltService
import com.dscorp.wispadmin.wispadmin.service.OltService
import com.dscorp.wispadmin.wispadmin.service.RealOltService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class OltServiceConfig {

    @Bean
    @Primary
    fun oltService(
        properties: OltServiceProperties,
        realOltService: RealOltService,
        mockOltService: MockOltService
    ): OltService {
        return if (properties.mock.enabled) {
            mockOltService
        } else {
            realOltService
        }
    }
}
