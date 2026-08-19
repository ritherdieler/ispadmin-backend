package com.dscorp.wispadmin.oltgateway.config

import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrAuditLogRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuStatusCurrentRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuTypeRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrSyncRunRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrTaskRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrZoneRepository
import com.dscorp.wispadmin.oltgateway.mapper.SmartOltCompatMapper
import com.dscorp.wispadmin.oltgateway.parser.AutofindParser
import com.dscorp.wispadmin.oltgateway.parser.BoardParser
import com.dscorp.wispadmin.oltgateway.parser.OnuInfoBySnParser
import com.dscorp.wispadmin.oltgateway.parser.OnuSummaryParser
import com.dscorp.wispadmin.oltgateway.parser.OpticalInfoParser
import com.dscorp.wispadmin.oltgateway.parser.VersionParser
import com.dscorp.wispadmin.oltgateway.service.MockOltGatewayQueryService
import com.dscorp.wispadmin.oltgateway.service.OltGatewayCommandService
import com.dscorp.wispadmin.oltgateway.service.OltGatewayQueryFacade
import com.dscorp.wispadmin.oltgateway.service.OltGatewayQueryService
import com.dscorp.wispadmin.oltgateway.service.OltInventorySyncScheduler
import com.dscorp.wispadmin.oltgateway.service.OltInventorySyncService
import com.dscorp.wispadmin.oltgateway.service.OltManagerFacade
import com.dscorp.wispadmin.oltgateway.service.OltSignalPollScheduler
import com.dscorp.wispadmin.oltgateway.service.OltSignalPollService
import com.dscorp.wispadmin.oltgateway.service.SignalCategoryCalculator
import com.dscorp.wispadmin.oltgateway.service.inventory.ParallelOnuInventoryReader
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import com.dscorp.wispadmin.oltgateway.ssh.OltCommandExecutor
import com.dscorp.wispadmin.oltgateway.ssh.OltSshClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Configuration
@EnableConfigurationProperties(OltGatewayProperties::class)
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class OltGatewayConfig {

    @Bean
    fun oltGatewayApiKeyFilterRegistration(
        properties: OltGatewayProperties,
        objectMapper: ObjectMapper
    ): FilterRegistrationBean<OltGatewayApiKeyFilter> {
        val registration = FilterRegistrationBean<OltGatewayApiKeyFilter>()
        registration.filter = OltGatewayApiKeyFilter(properties, objectMapper)
        registration.addUrlPatterns("/api/olt-gateway/*", "/api/onu/*")
        registration.order = 25
        return registration
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "olt.gateway.mock", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun oltSshClient(properties: OltGatewayProperties): OltSshClient {
        return OltSshClient(properties)
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "olt.gateway.mock", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun oltCliBus(
        oltSshClient: OltSshClient,
        properties: OltGatewayProperties
    ): OltCliBus {
        properties.session.poolSize = 1
        return OltCliBus(oltSshClient, properties).also { it.start() }
    }

    @Bean
    @ConditionalOnProperty(prefix = "olt.gateway.mock", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun oltCommandExecutor(oltCliBus: OltCliBus): OltCommandExecutor {
        return OltCommandExecutor(oltCliBus)
    }

    @Bean
    @ConditionalOnProperty(prefix = "olt.gateway.mock", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun parallelOnuInventoryReader(
        oltCliBus: OltCliBus,
        boardParser: BoardParser,
        onuSummaryParser: OnuSummaryParser,
        oltRepository: OltMgrOltRepository,
        properties: OltGatewayProperties
    ): ParallelOnuInventoryReader {
        return ParallelOnuInventoryReader(
            cliBus = oltCliBus,
            boardParser = boardParser,
            onuSummaryParser = onuSummaryParser,
            oltRepository = oltRepository,
            properties = properties
        )
    }

    @Bean
    @ConditionalOnProperty(prefix = "olt.gateway.mock", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun oltGatewayQueryService(
        oltCommandExecutor: OltCommandExecutor,
        parallelOnuInventoryReader: ParallelOnuInventoryReader,
        oltRepository: OltMgrOltRepository,
        smartOltCompatMapper: SmartOltCompatMapper,
        properties: OltGatewayProperties,
        versionParser: VersionParser,
        boardParser: BoardParser,
        autofindParser: AutofindParser,
        onuInfoBySnParser: OnuInfoBySnParser,
        opticalInfoParser: OpticalInfoParser
    ): OltGatewayQueryFacade {
        return OltGatewayQueryService(
            commandExecutor = oltCommandExecutor,
            inventoryReader = parallelOnuInventoryReader,
            oltRepository = oltRepository,
            smartOltCompatMapper = smartOltCompatMapper,
            properties = properties,
            versionParser = versionParser,
            boardParser = boardParser,
            autofindParser = autofindParser,
            onuInfoBySnParser = onuInfoBySnParser,
            opticalInfoParser = opticalInfoParser
        )
    }

    @Bean
    @ConditionalOnProperty(prefix = "olt.gateway.mock", name = ["enabled"], havingValue = "true")
    fun mockOltGatewayQueryService(
        properties: OltGatewayProperties,
        smartOltCompatMapper: SmartOltCompatMapper,
        versionParser: VersionParser,
        boardParser: BoardParser,
        autofindParser: AutofindParser,
        onuInfoBySnParser: OnuInfoBySnParser,
        onuSummaryParser: OnuSummaryParser,
        opticalInfoParser: OpticalInfoParser
    ): OltGatewayQueryFacade {
        return MockOltGatewayQueryService(
            properties = properties,
            smartOltCompatMapper = smartOltCompatMapper,
            versionParser = versionParser,
            boardParser = boardParser,
            autofindParser = autofindParser,
            onuInfoBySnParser = onuInfoBySnParser,
            onuSummaryParser = onuSummaryParser,
            opticalInfoParser = opticalInfoParser
        )
    }

    @Bean
    @ConditionalOnProperty(prefix = "olt.gateway.mock", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun oltGatewayCommandService(
        oltCommandExecutor: OltCommandExecutor,
        properties: OltGatewayProperties
    ): OltGatewayCommandService {
        return OltGatewayCommandService(
            runCommand = oltCommandExecutor::run,
            properties = properties,
            inWriteJob = { block -> oltCommandExecutor.write { block() } }
        )
    }

    @Bean
    @ConditionalOnProperty(prefix = "olt.gateway.mock", name = ["enabled"], havingValue = "true")
    fun mockOltGatewayCommandService(properties: OltGatewayProperties): OltGatewayCommandService {
        return OltGatewayCommandService(
            runCommand = { "Success\nMA5608T#" },
            properties = properties
        )
    }

    @Bean
    fun oltManagerFacade(
        oltRepository: OltMgrOltRepository,
        zoneRepository: OltMgrZoneRepository,
        onuTypeRepository: OltMgrOnuTypeRepository,
        onuRepository: OltMgrOnuRepository,
        statusRepository: OltMgrOnuStatusCurrentRepository,
        taskRepository: OltMgrTaskRepository,
        auditLogRepository: OltMgrAuditLogRepository,
        commandService: OltGatewayCommandService,
        queryFacade: OltGatewayQueryFacade,
        mapper: SmartOltCompatMapper,
        properties: OltGatewayProperties
    ): OltManagerFacade {
        return OltManagerFacade(
            oltRepository = oltRepository,
            zoneRepository = zoneRepository,
            onuTypeRepository = onuTypeRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            taskRepository = taskRepository,
            auditLogRepository = auditLogRepository,
            commandService = commandService,
            queryFacade = queryFacade,
            mapper = mapper,
            properties = properties
        )
    }

    @Bean
    fun oltInventorySyncService(
        queryFacade: OltGatewayQueryFacade,
        oltRepository: OltMgrOltRepository,
        onuRepository: OltMgrOnuRepository,
        statusRepository: OltMgrOnuStatusCurrentRepository,
        auditLogRepository: OltMgrAuditLogRepository,
        syncRunRepository: OltMgrSyncRunRepository,
        taskRepository: OltMgrTaskRepository,
        properties: OltGatewayProperties,
        cliBus: ObjectProvider<OltCliBus>,
        transactionManager: PlatformTransactionManager
    ): OltInventorySyncService {
        return OltInventorySyncService(
            queryFacade = queryFacade,
            oltRepository = oltRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            auditLogRepository = auditLogRepository,
            syncRunRepository = syncRunRepository,
            taskRepository = taskRepository,
            properties = properties,
            cliBus = cliBus.ifAvailable,
            transactionTemplate = TransactionTemplate(transactionManager)
        )
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "olt.gateway.sync",
        name = ["inventory-enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun oltInventorySyncScheduler(syncService: OltInventorySyncService): OltInventorySyncScheduler {
        return OltInventorySyncScheduler(syncService)
    }

    @Bean
    fun oltSignalPollService(
        oltRepository: OltMgrOltRepository,
        onuRepository: OltMgrOnuRepository,
        statusRepository: OltMgrOnuStatusCurrentRepository,
        taskRepository: OltMgrTaskRepository,
        boardParser: BoardParser,
        opticalInfoParser: OpticalInfoParser,
        signalCategoryCalculator: SignalCategoryCalculator,
        properties: OltGatewayProperties,
        cliBus: ObjectProvider<OltCliBus>
    ): OltSignalPollService {
        return OltSignalPollService(
            oltRepository = oltRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            taskRepository = taskRepository,
            boardParser = boardParser,
            opticalInfoParser = opticalInfoParser,
            signalCategoryCalculator = signalCategoryCalculator,
            properties = properties,
            cliBus = cliBus.ifAvailable
        )
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "olt.gateway.sync",
        name = ["signal-enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun oltSignalPollScheduler(signalPollService: OltSignalPollService): OltSignalPollScheduler {
        return OltSignalPollScheduler(signalPollService)
    }
}
