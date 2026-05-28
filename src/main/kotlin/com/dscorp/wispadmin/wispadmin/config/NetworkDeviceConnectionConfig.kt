package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnectionHelper
import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnectionManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct

@Configuration
class NetworkDeviceConnectionConfig {
    
    @Autowired
    private lateinit var helper: NetworkDeviceConnectionHelper
    
    @PostConstruct
    fun init() {
        NetworkDeviceConnectionManager.setHelper(helper)
    }
}
