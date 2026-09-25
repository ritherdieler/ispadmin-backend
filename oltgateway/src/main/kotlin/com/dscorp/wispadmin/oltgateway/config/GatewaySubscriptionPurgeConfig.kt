package com.dscorp.wispadmin.oltgateway.config

import com.dscorp.wispadmin.oltgateway.service.GatewayInventoryPurge
import com.dscorp.wispadmin.oltgateway.service.GatewaySubscriptionPurgeService
import com.dscorp.wispadmin.oltgateway.service.OnuGovernanceDeleteService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

@Configuration
class GatewaySubscriptionPurgeConfig {
    @Bean
    fun gatewaySubscriptionPurgeService(
        governance: OnuGovernanceDeleteService,
        @Qualifier("oltGatewayDataSource") dataSource: DataSource,
    ) = GatewaySubscriptionPurgeService(
        deleteOnu = { governance.deleteBySn(it) },
        deleteInventory = { GatewayInventoryPurge(JdbcTemplate(dataSource)).purge(it) },
    )
}
