package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.service.SmartOltMgmtIpDhcpApplier
import com.dscorp.wispadmin.wispadmin.service.SmartOltMgmtVlanPolicy
import com.dscorp.wispadmin.wispadmin.util.SmartOltHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SmartOltMgmtIpConfig {

    @Bean
    fun smartOltMgmtVlanPolicy(
        @Value("\${olt.smartolt.customer-vlans-with-mgmt:100}") customerVlansRaw: String,
        @Value("\${olt.smartolt.mgmt-vlan:1000}") mgmtVlan: String,
    ): SmartOltMgmtVlanPolicy {
        return SmartOltMgmtVlanPolicy(
            customerVlans = customerVlansRaw.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet(),
            mgmtVlan = mgmtVlan,
        )
    }

    @Bean
    fun smartOltMgmtIpDhcpApplier(
        policy: SmartOltMgmtVlanPolicy,
        client: SmartOltHttpClient,
    ): SmartOltMgmtIpDhcpApplier {
        return SmartOltMgmtIpDhcpApplier(
            policy = policy,
            poster = { path, body -> client.post(path, body, Any::class.java) },
        )
    }
}
