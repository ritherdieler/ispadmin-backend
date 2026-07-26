package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.wispadmin.extensions.MikrotikClientAccessor
import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnectionHelper
import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnectionManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct

@Configuration
class NetworkDeviceConnectionConfig {
    
    @Autowired
    private lateinit var helper: NetworkDeviceConnectionHelper

    @Autowired
    private lateinit var mikrotikClient: MikrotikClient

    @Autowired
    private lateinit var routerOsClientProperties: RouterOsClientProperties
    
    @PostConstruct
    fun init() {
        NetworkDeviceConnectionManager.setHelper(helper)
        MikrotikClientAccessor.setClient(mikrotikClient, routerOsClientProperties)
    }
}
