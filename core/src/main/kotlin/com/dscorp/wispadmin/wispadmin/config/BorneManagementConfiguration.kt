package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(BorneManagementProperties::class)
class BorneManagementConfiguration
