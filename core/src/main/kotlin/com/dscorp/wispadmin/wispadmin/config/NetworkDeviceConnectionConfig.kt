package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnectionHelper
import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnectionManager
import com.dscorp.wispadmin.wispadmin.trafficclient.TrafficRouterOsCommandUseCase
import com.dscorp.wispadmin.wispadmin.trafficclient.TrafficRouterOsGatewayAccessor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct

@Configuration
class NetworkDeviceConnectionConfig {

    @Autowired
    private lateinit var helper: NetworkDeviceConnectionHelper

    @Autowired
    private lateinit var routerOsCommands: TrafficRouterOsCommandUseCase

    @PostConstruct
    fun init() {
        NetworkDeviceConnectionManager.setHelper(helper)
        TrafficRouterOsGatewayAccessor.setUseCase(routerOsCommands)
    }
}
