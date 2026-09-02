package com.dscorp.wispadmin.oltgateway.config

import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrAuditLogRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuAutofindRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuStatusCurrentRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuTypeRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrSyncRunRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrTaskRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrZoneRepository
import com.dscorp.wispadmin.oltgateway.mapper.SmartOltCompatMapper
import com.dscorp.wispadmin.oltgateway.parser.AutofindParser
import com.dscorp.wispadmin.oltgateway.parser.HuaweiOltAlarmParser
import com.dscorp.wispadmin.oltgateway.parser.BoardParser
import com.dscorp.wispadmin.oltgateway.parser.OnuInfoBySnParser
import com.dscorp.wispadmin.oltgateway.parser.OnuSummaryParser
import com.dscorp.wispadmin.oltgateway.parser.OpticalInfoParser
import com.dscorp.wispadmin.oltgateway.parser.VersionParser
import com.dscorp.wispadmin.oltgateway.service.MockOltGatewayQueryService
import com.dscorp.wispadmin.oltgateway.service.OltAutofindCacheService
import com.dscorp.wispadmin.oltgateway.service.OltAutofindCacheWriter
import com.dscorp.wispadmin.oltgateway.service.OltAutofindRefreshScheduler
import com.dscorp.wispadmin.oltgateway.service.OltGatewayCommandService
import com.dscorp.wispadmin.oltgateway.service.OltGatewaySyncJobRunner
import com.dscorp.wispadmin.oltgateway.service.OltGatewayQueryFacade
import com.dscorp.wispadmin.oltgateway.service.OltGatewayQueryService
import com.dscorp.wispadmin.oltgateway.service.LabOpticalSshPollService
import com.dscorp.wispadmin.oltgateway.service.LabOpticalSshScheduler
import com.dscorp.wispadmin.oltgateway.service.OltInventorySyncScheduler
import com.dscorp.wispadmin.oltgateway.service.OltInventorySyncService
import com.dscorp.wispadmin.oltgateway.service.OltManagerFacade
import com.dscorp.wispadmin.oltgateway.service.OnuExternalIdBackfillService
import com.dscorp.wispadmin.oltgateway.service.OltSignalPollScheduler
import com.dscorp.wispadmin.oltgateway.service.OltSignalPollService
import com.dscorp.wispadmin.oltgateway.service.SignalCategoryCalculator
import com.dscorp.wispadmin.oltgateway.service.SmartOltImportService
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltCatalogClient
import com.dscorp.wispadmin.oltgateway.service.inventory.ParallelOnuInventoryReader
import com.dscorp.wispadmin.oltgateway.snmp.OltSnmpBusRegistry
import com.dscorp.wispadmin.oltgateway.snmp.OltSnmpClient
import com.dscorp.wispadmin.oltgateway.snmp.OltSnmpTrapReceiver
import com.dscorp.wispadmin.oltgateway.snmp.RecentOltSnmpTrapBuffer
import com.dscorp.wispadmin.oltgateway.snmp.Snmp4jOltSnmpClient
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
import org.springframework.context.annotation.Profile
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration
@EnableConfigurationProperties(OltGatewayProperties::class)
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class OltGatewayConfig {

    @Bean
    fun huaweiOltAlarmParser(): HuaweiOltAlarmParser = HuaweiOltAlarmParser()

    @Bean
    fun oltGatewayApiKeyFilterRegistration(
        properties: OltGatewayProperties,
        objectMapper: ObjectMapper
    ): FilterRegistrationBean<OltGatewayApiKeyFilter> {
        val registration = FilterRegistrationBean<OltGatewayApiKeyFilter>()
        registration.filter = OltGatewayApiKeyFilter(properties, objectMapper)
        registration.addUrlPatterns("/api/olt-gateway/*")
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
        return OltCliBus(oltSshClient, properties).also { it.start() }
    }

    @Bean
    @ConditionalOnProperty(prefix = "olt.gateway.mock", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun oltCommandExecutor(oltCliBus: OltCliBus): OltCommandExecutor {
        return OltCommandExecutor(oltCliBus)
    }

    @Bean
    @ConditionalOnProperty(prefix = "olt.gateway.mock", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    @Suppress("DEPRECATION")
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
        opticalInfoParser: OpticalInfoParser,
        snmpClient: ObjectProvider<OltSnmpClient>
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
            opticalInfoParser = opticalInfoParser,
            snmpClient = snmpClient.ifAvailable
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
    fun onuExternalIdBackfillService(
        oltRepository: OltMgrOltRepository,
        onuRepository: OltMgrOnuRepository,
        properties: OltGatewayProperties
    ): OnuExternalIdBackfillService {
        return OnuExternalIdBackfillService(
            oltRepository = oltRepository,
            onuRepository = onuRepository,
            properties = properties
        )
    }

    @Bean
    fun oltAutofindCacheWriter(
        autofindRepository: OltMgrOnuAutofindRepository,
        onuRepository: OltMgrOnuRepository,
        properties: OltGatewayProperties
    ): OltAutofindCacheWriter {
        return OltAutofindCacheWriter(
            autofindRepository = autofindRepository,
            onuRepository = onuRepository,
            properties = properties
        )
    }

    @Bean
    fun oltAutofindCacheService(
        queryFacade: OltGatewayQueryFacade,
        oltAutofindCacheWriter: OltAutofindCacheWriter,
        properties: OltGatewayProperties
    ): OltAutofindCacheService {
        return OltAutofindCacheService(
            queryFacade = queryFacade,
            writer = oltAutofindCacheWriter,
            properties = properties
        )
    }

    @Bean
    @ConditionalOnProperty(prefix = "olt.gateway.autofind", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun oltAutofindRefreshScheduler(
        oltAutofindCacheService: OltAutofindCacheService
    ): OltAutofindRefreshScheduler {
        return OltAutofindRefreshScheduler(oltAutofindCacheService)
    }

    @Bean(destroyMethod = "shutdown")
    fun oltGatewaySyncExecutor(): ExecutorService {
        return Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "olt-gateway-sync-job").apply { isDaemon = true }
        }
    }

    @Bean
    fun oltGatewaySyncJobRunner(
        inventorySyncService: OltInventorySyncService,
        signalPollService: OltSignalPollService,
        oltGatewaySyncExecutor: ExecutorService
    ): OltGatewaySyncJobRunner {
        return OltGatewaySyncJobRunner(
            inventorySyncService = inventorySyncService,
            signalPollService = signalPollService,
            executor = oltGatewaySyncExecutor
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
    @ConditionalOnProperty(prefix = "olt.gateway.snmp", name = ["enabled"], havingValue = "true")
    fun oltSnmpBusRegistry(properties: OltGatewayProperties): OltSnmpBusRegistry {
        return OltSnmpBusRegistry(acquireTimeoutMs = properties.snmp.acquireTimeoutMs)
    }

    @Bean
    @ConditionalOnProperty(prefix = "olt.gateway.snmp", name = ["enabled"], havingValue = "true")
    fun oltSnmpClient(
        properties: OltGatewayProperties,
        oltSnmpBusRegistry: OltSnmpBusRegistry
    ): OltSnmpClient {
        return Snmp4jOltSnmpClient(properties, oltSnmpBusRegistry)
    }

    @Bean
    fun recentOltSnmpTrapBuffer(properties: OltGatewayProperties): RecentOltSnmpTrapBuffer {
        return RecentOltSnmpTrapBuffer(properties.snmp.trap.bufferSize)
    }

    @Bean(destroyMethod = "close")
    @Profile("!staging")
    @ConditionalOnProperty(prefix = "olt.gateway.snmp.trap", name = ["enabled"], havingValue = "true")
    fun oltSnmpTrapReceiver(
        properties: OltGatewayProperties,
        recentOltSnmpTrapBuffer: RecentOltSnmpTrapBuffer
    ): OltSnmpTrapReceiver {
        return OltSnmpTrapReceiver(properties, recentOltSnmpTrapBuffer).also { it.start() }
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
        zoneRepository: OltMgrZoneRepository,
        onuTypeRepository: OltMgrOnuTypeRepository,
        properties: OltGatewayProperties,
        cliBus: ObjectProvider<OltCliBus>,
        snmpClient: ObjectProvider<OltSnmpClient>,
        transactionManager: PlatformTransactionManager,
        eventPublisher: org.springframework.context.ApplicationEventPublisher
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
            snmpClient = snmpClient.ifAvailable,
            transactionTemplate = TransactionTemplate(transactionManager),
            zoneRepository = zoneRepository,
            onuTypeRepository = onuTypeRepository,
            eventPublisher = eventPublisher
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
        cliBus: ObjectProvider<OltCliBus>,
        snmpClient: ObjectProvider<OltSnmpClient>,
        eventPublisher: org.springframework.context.ApplicationEventPublisher
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
            cliBus = cliBus.ifAvailable,
            snmpClient = snmpClient.ifAvailable,
            eventPublisher = eventPublisher
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

    @Bean
    @ConditionalOnProperty(
        prefix = "olt.gateway.sync",
        name = ["lab-optical-ssh-enabled"],
        havingValue = "true",
    )
    fun labOpticalSshScheduler(pollService: LabOpticalSshPollService): LabOpticalSshScheduler {
        return LabOpticalSshScheduler(pollService)
    }

    @Bean
    fun smartOltImportService(
        catalogClient: SmartOltCatalogClient,
        oltRepository: OltMgrOltRepository,
        zoneRepository: OltMgrZoneRepository,
        onuTypeRepository: OltMgrOnuTypeRepository,
        onuRepository: OltMgrOnuRepository,
        statusRepository: OltMgrOnuStatusCurrentRepository,
        auditLogRepository: OltMgrAuditLogRepository,
        properties: OltGatewayProperties
    ): SmartOltImportService {
        return SmartOltImportService(
            catalogClient = catalogClient,
            oltRepository = oltRepository,
            zoneRepository = zoneRepository,
            onuTypeRepository = onuTypeRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            auditLogRepository = auditLogRepository,
            properties = properties
        )
    }
}
