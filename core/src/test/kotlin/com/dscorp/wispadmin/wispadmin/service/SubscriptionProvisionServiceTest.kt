package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.MikrotikProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.repository.PlaceRepository
import com.dscorp.wispadmin.wispadmin.repository.PlanRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivateResponse
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.IInstallationStrategy
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.InstallationResult
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.InstallationStrategyFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.LocalDateTime
import java.util.Optional

class SubscriptionProvisionServiceTest {

    private val repository = mockk<SubscriptionRepository>()
    private val networkDeviceRepository = mockk<NetworkDeviceRepository>()
    private val planRepository = mockk<PlanRepository>()
    private val placeRepository = mockk<PlaceRepository>()
    private val installationStrategyFactory = mockk<InstallationStrategyFactory>()
    private val installationStrategy = mockk<IInstallationStrategy>()
    private val errorLogRepository = mockk<ErrorLogRepository>(relaxed = true)
    private val gatewayClient = mockk<GatewayOnuActivationClient>()
    private val gatewayActivation = mockk<ObjectProvider<GatewayOnuActivationClient>>()
    private val pppoeAccessService = mockk<com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeAccessService>(relaxed = true)

    private val service = SubscriptionProvisionService(
        repository = repository,
        networkDeviceRepository = networkDeviceRepository,
        planRepository = planRepository,
        placeRepository = placeRepository,
        installationStrategyFactory = installationStrategyFactory,
        errorLogRepository = errorLogRepository,
        gatewayActivation = gatewayActivation,
        pppoeAccessService = pppoeAccessService,
        cpeEnabled = false,
    )

    init {
        every { gatewayActivation.ifAvailable } returns null
    }

    @Test
    fun `initializeStatuses sets wireless mikrotik pending and olt NA`() {
        val subscription = baseSubscription()
        service.initializeStatuses(subscription, InstallationType.WIRELESS)
        assertEquals(MikrotikProvisionStatus.PENDING, subscription.mikrotikProvisionStatus)
        assertEquals(OltProvisionStatus.NA, subscription.oltProvisionStatus)
        assertEquals(
            com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus.NA,
            subscription.tr069ProvisionStatus
        )
    }

    @Test
    fun `initializeStatuses sets fiber both pending`() {
        val subscription = baseSubscription()
        service.initializeStatuses(subscription, InstallationType.FIBER)
        assertEquals(MikrotikProvisionStatus.PENDING, subscription.mikrotikProvisionStatus)
        assertEquals(OltProvisionStatus.PENDING, subscription.oltProvisionStatus)
        assertEquals(
            com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus.NA,
            subscription.tr069ProvisionStatus
        )
    }

    @Test
    fun `initializeStatuses sets fiber tr069 PENDING when genieacs enabled`() {
        val enabledService = SubscriptionProvisionService(
            repository = repository,
            networkDeviceRepository = networkDeviceRepository,
            planRepository = planRepository,
            placeRepository = placeRepository,
            installationStrategyFactory = installationStrategyFactory,
            errorLogRepository = errorLogRepository,
            gatewayActivation = mockk<ObjectProvider<GatewayOnuActivationClient>>().also {
                every { it.ifAvailable } returns null
            },
            pppoeAccessService = pppoeAccessService,
            cpeEnabled = true,
        )
        val subscription = baseSubscription()
        enabledService.initializeStatuses(subscription, InstallationType.FIBER)
        assertEquals(
            com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus.PENDING,
            subscription.tr069ProvisionStatus
        )
    }

    @Test
    fun `initializeStatuses ONLY_TV CATV sets olt and tr069 NA`() {
        val subscription = baseSubscription().apply { fiberOnuSn = null }
        service.initializeStatuses(subscription, InstallationType.ONLY_TV_FIBER)
        assertEquals(MikrotikProvisionStatus.COMPLETE, subscription.mikrotikProvisionStatus)
        assertEquals(OltProvisionStatus.NA, subscription.oltProvisionStatus)
        assertEquals(Tr069ProvisionStatus.NA, subscription.tr069ProvisionStatus)
    }

    @Test
    fun `initializeStatuses ONLY_TV with ONU sets olt and tr069 PENDING when ACS on`() {
        val enabledService = SubscriptionProvisionService(
            repository = repository,
            networkDeviceRepository = networkDeviceRepository,
            planRepository = planRepository,
            placeRepository = placeRepository,
            installationStrategyFactory = installationStrategyFactory,
            errorLogRepository = errorLogRepository,
            gatewayActivation = mockk<ObjectProvider<GatewayOnuActivationClient>>().also {
                every { it.ifAvailable } returns null
            },
            pppoeAccessService = pppoeAccessService,
            cpeEnabled = true,
        )
        val subscription = baseSubscription().apply {
            fiberOnuSn = "VSOL0031C0B6"
        }
        enabledService.initializeStatuses(subscription, InstallationType.ONLY_TV_FIBER)
        assertEquals(MikrotikProvisionStatus.COMPLETE, subscription.mikrotikProvisionStatus)
        assertEquals(OltProvisionStatus.PENDING, subscription.oltProvisionStatus)
        assertEquals(Tr069ProvisionStatus.PENDING, subscription.tr069ProvisionStatus)
    }

    @Test
    fun `applyInstallationResult ONLY_TV with ONU marks olt COMPLETE`() {
        val subscription = baseSubscription().apply {
            installationType = InstallationType.ONLY_TV_FIBER
            fiberOnuSn = "VSOL0031C0B6"
            mikrotikProvisionStatus = MikrotikProvisionStatus.COMPLETE
            oltProvisionStatus = OltProvisionStatus.PENDING
            tr069ProvisionStatus = Tr069ProvisionStatus.PENDING
        }
        service.applyInstallationResult(
            subscription,
            InstallationResult(queueAdded = false, onuAuthorized = true, onuSn = "VSOL0031C0B6"),
            InstallationType.ONLY_TV_FIBER,
        )
        assertEquals(MikrotikProvisionStatus.COMPLETE, subscription.mikrotikProvisionStatus)
        assertEquals(OltProvisionStatus.COMPLETE, subscription.oltProvisionStatus)
        assertEquals(Tr069ProvisionStatus.PENDING, subscription.tr069ProvisionStatus)
    }

    @Test
    fun `applyInstallationResult ONLY_TV CATV keeps olt and tr069 NA`() {
        val subscription = baseSubscription().apply {
            installationType = InstallationType.ONLY_TV_FIBER
            mikrotikProvisionStatus = MikrotikProvisionStatus.COMPLETE
            oltProvisionStatus = OltProvisionStatus.NA
            tr069ProvisionStatus = Tr069ProvisionStatus.NA
        }
        service.applyInstallationResult(
            subscription,
            InstallationResult(queueAdded = false),
            InstallationType.ONLY_TV_FIBER,
        )
        assertEquals(OltProvisionStatus.NA, subscription.oltProvisionStatus)
        assertEquals(Tr069ProvisionStatus.NA, subscription.tr069ProvisionStatus)
    }

    @Test
    fun `applyInstallationResult marks complete and clears retry when queue ok`() {
        val subscription = baseSubscription().apply {
            mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
            oltProvisionStatus = OltProvisionStatus.NA
        }
        service.applyInstallationResult(
            subscription,
            InstallationResult(queueAdded = true),
            InstallationType.WIRELESS
        )
        assertEquals(MikrotikProvisionStatus.COMPLETE, subscription.mikrotikProvisionStatus)
        assertFalse(subscription.isProvisioningPending())
        assertNull(subscription.provisionNextAttemptAt)
    }

    @Test
    fun `applyInstallationResult schedules backoff when mikrotik pending`() {
        val subscription = baseSubscription().apply {
            mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
            oltProvisionStatus = OltProvisionStatus.NA
            provisionAttemptCount = 0
        }
        service.applyInstallationResult(
            subscription,
            InstallationResult(queueAdded = false, mikrotikError = "down"),
            InstallationType.WIRELESS
        )
        assertEquals(MikrotikProvisionStatus.PENDING, subscription.mikrotikProvisionStatus)
        assertEquals(1, subscription.provisionAttemptCount)
        assertNotNull(subscription.provisionNextAttemptAt)
        assertTrue(subscription.provisionNextAttemptAt!!.isAfter(LocalDateTime.now().plusMinutes(4)))
        assertEquals("down", subscription.provisionLastError)
    }

    @Test
    fun `applyInstallationResult marks olt FAILED when fiber onu has oltError`() {
        val subscription = baseSubscription().apply {
            mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
            oltProvisionStatus = OltProvisionStatus.PENDING
        }
        service.applyInstallationResult(
            subscription,
            InstallationResult(queueAdded = true, onuAuthorized = false, oltError = "olt timeout"),
            InstallationType.FIBER
        )
        assertEquals(MikrotikProvisionStatus.COMPLETE, subscription.mikrotikProvisionStatus)
        assertEquals(OltProvisionStatus.FAILED, subscription.oltProvisionStatus)
        assertTrue(subscription.provisionLastError!!.contains("olt timeout"))
    }

    @Test
    fun `applyInstallationResult keeps olt PENDING when fiber onu unauthorized without oltError`() {
        val subscription = baseSubscription().apply {
            mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
            oltProvisionStatus = OltProvisionStatus.PENDING
        }
        service.applyInstallationResult(
            subscription,
            InstallationResult(queueAdded = true, onuAuthorized = false),
            InstallationType.FIBER
        )
        assertEquals(OltProvisionStatus.PENDING, subscription.oltProvisionStatus)
    }

    @Test
    fun `applyInstallationResult marks olt FAILED when ONLY_TV onu has oltError`() {
        val subscription = baseSubscription().apply {
            installationType = InstallationType.ONLY_TV_FIBER
            fiberOnuSn = "VSOL0031C0B6"
            mikrotikProvisionStatus = MikrotikProvisionStatus.COMPLETE
            oltProvisionStatus = OltProvisionStatus.PENDING
            tr069ProvisionStatus = Tr069ProvisionStatus.PENDING
        }
        service.applyInstallationResult(
            subscription,
            InstallationResult(
                queueAdded = false,
                onuAuthorized = false,
                onuSn = "VSOL0031C0B6",
                oltError = "OLT activate failed",
            ),
            InstallationType.ONLY_TV_FIBER,
        )
        assertEquals(OltProvisionStatus.FAILED, subscription.oltProvisionStatus)
    }

    @Test
    fun `scheduleNextAttempt treats null attempt count as zero`() {
        val subscription = baseSubscription().apply {
            mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
            oltProvisionStatus = OltProvisionStatus.NA
            provisionAttemptCount = null
        }
        service.scheduleNextAttempt(subscription)
        assertEquals(1, subscription.provisionAttemptCount)
        assertNotNull(subscription.provisionNextAttemptAt)
    }

    @Test
    fun `scheduleNextAttempt uses 15 minutes on second attempt`() {
        val subscription = baseSubscription().apply {
            mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
            oltProvisionStatus = OltProvisionStatus.NA
            provisionAttemptCount = 1
        }
        val before = LocalDateTime.now()
        service.scheduleNextAttempt(subscription)
        assertEquals(2, subscription.provisionAttemptCount)
        assertNotNull(subscription.provisionNextAttemptAt)
        assertTrue(subscription.provisionNextAttemptAt!!.isAfter(before.plusMinutes(14)))
        assertTrue(subscription.provisionNextAttemptAt!!.isBefore(before.plusMinutes(16)))
    }

    @Test
    fun `scheduleNextAttempt uses 30 minutes from third attempt`() {
        val subscription = baseSubscription().apply {
            mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
            oltProvisionStatus = OltProvisionStatus.NA
            provisionAttemptCount = 2
        }
        val before = LocalDateTime.now()
        service.scheduleNextAttempt(subscription)
        assertEquals(3, subscription.provisionAttemptCount)
        assertNotNull(subscription.provisionNextAttemptAt)
        assertTrue(subscription.provisionNextAttemptAt!!.isAfter(before.plusMinutes(29)))
        assertTrue(subscription.provisionNextAttemptAt!!.isBefore(before.plusMinutes(31)))
    }

    @Test
    fun `scheduleNextAttempt marks failed after max attempts`() {
        val subscription = baseSubscription().apply {
            mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
            oltProvisionStatus = OltProvisionStatus.PENDING
            provisionAttemptCount = SubscriptionProvisionService.MAX_ATTEMPTS
        }
        service.scheduleNextAttempt(subscription)
        assertEquals(MikrotikProvisionStatus.FAILED, subscription.mikrotikProvisionStatus)
        assertEquals(OltProvisionStatus.FAILED, subscription.oltProvisionStatus)
        assertNull(subscription.provisionNextAttemptAt)
    }

    @Test
    fun `reconcile completes pending mikrotik when strategy succeeds`() {
        val subscription = baseSubscription().apply {
            id = 10
            mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
            oltProvisionStatus = OltProvisionStatus.NA
            hostDevice = NetworkDevice(id = 1)
            plan = Plan(id = 1)
            place = Place(id = 1)
        }
        every { networkDeviceRepository.findById(1) } returns Optional.of(NetworkDevice(id = 1))
        every { planRepository.findById(1) } returns Optional.of(Plan(id = 1))
        every { placeRepository.findById(1) } returns Optional.of(Place(id = 1))
        every { installationStrategyFactory.getStrategy(any()) } returns installationStrategy
        every {
            installationStrategy.processInstallation(any(), any(), any(), any(), any())
        } returns InstallationResult(queueAdded = true)
        every { repository.save(any()) } answers { firstArg() }

        val result = service.reconcile(subscription, sampleRequest())

        assertEquals(MikrotikProvisionStatus.COMPLETE, result.mikrotikProvisionStatus)
        assertFalse(result.isProvisioningPending())
        verify(exactly = 1) { repository.save(subscription) }
    }

    @Test
    fun `buildRequestFromSubscription includes vlan and wifi`() {
        val subscription = baseSubscription().apply {
            vlan = "100"
            wifiSsid24 = "acs2g"
            wifiSsid5 = "acs5g"
            ip = "192.168.30.10"
            installationType = InstallationType.FIBER
        }
        val request = service.buildRequestFromSubscription(subscription)
        assertEquals("100", request.vlan)
        assertEquals("acs2g", request.wifiSsid24)
        assertEquals("acs5g", request.wifiSsid5)
        assertEquals("192.168.30.10", request.clientIpAddress)
    }

    @Test
    fun `refreshTr069FromGateway persists COMPLETE from gateway while PENDING`() {
        val subscription = baseSubscription().apply {
            id = 42
            installationType = InstallationType.FIBER
            fiberOnuSn = "ZTEGDC47BFFD"
            oltProvisionStatus = OltProvisionStatus.COMPLETE
            tr069ProvisionStatus = Tr069ProvisionStatus.PENDING
        }
        every { gatewayActivation.ifAvailable } returns gatewayClient
        every { gatewayClient.activationBySn("ZTEGDC47BFFD") } returns GatewayOnuActivateResponse(
            uniqueExternalId = "ext-1",
            sn = "ZTEGDC47BFFD",
            oltStatus = "COMPLETE",
            cpeStatus = "COMPLETE",
        )
        every { repository.save(subscription) } returns subscription

        val result = service.refreshTr069FromGateway(subscription)

        assertEquals(Tr069ProvisionStatus.COMPLETE, result.tr069ProvisionStatus)
        verify(exactly = 1) { repository.save(subscription) }
    }

    @Test
    fun `refreshTr069FromGateway persists ACS deviceId and upserts subscription_acs`() {
        val acsSync = mockk<com.dscorp.wispadmin.wispadmin.service.genieacs.SubscriptionAcsSyncService>(relaxed = true)
        val tagger = mockk<com.dscorp.wispadmin.wispadmin.service.genieacs.GenieAcsSubscriptionTagger>(relaxed = true)
        val wired = SubscriptionProvisionService(
            repository = repository,
            networkDeviceRepository = networkDeviceRepository,
            planRepository = planRepository,
            placeRepository = placeRepository,
            installationStrategyFactory = installationStrategyFactory,
            errorLogRepository = errorLogRepository,
            gatewayActivation = gatewayActivation,
            pppoeAccessService = pppoeAccessService,
            cpeEnabled = false,
            acsSyncService = acsSync,
            acsTagger = tagger,
        )
        val subscription = baseSubscription().apply {
            id = 42
            installationType = InstallationType.FIBER
            fiberOnuSn = "ZTEGDC47BFFD"
            oltProvisionStatus = OltProvisionStatus.COMPLETE
            tr069ProvisionStatus = Tr069ProvisionStatus.PENDING
        }
        every { gatewayActivation.ifAvailable } returns gatewayClient
        every { gatewayClient.activationBySn("ZTEGDC47BFFD") } returns GatewayOnuActivateResponse(
            uniqueExternalId = "ext-1",
            sn = "ZTEGDC47BFFD",
            oltStatus = "COMPLETE",
            cpeStatus = "COMPLETE",
            deviceId = "5872C9-F6600R-ZTEGDC47BFFD",
        )
        every { repository.save(subscription) } returns subscription

        val result = wired.refreshTr069FromGateway(subscription)

        assertEquals(Tr069ProvisionStatus.COMPLETE, result.tr069ProvisionStatus)
        assertEquals("5872C9-F6600R-ZTEGDC47BFFD", result.tr069DeviceId)
        verify {
            acsSync.upsertFromProvision(
                42,
                match {
                    it.deviceId == "5872C9-F6600R-ZTEGDC47BFFD" &&
                        it.status == Tr069ProvisionStatus.COMPLETE
                },
                "ZTEGDC47BFFD",
            )
        }
        verify {
            tagger.apply(
                deviceId = "5872C9-F6600R-ZTEGDC47BFFD",
                subscriptionId = 42,
                kind = com.dscorp.wispadmin.wispadmin.service.genieacs.GenieAcsServiceKind.INTERNET,
                fullName = "Ana Lopez",
                previousDeviceId = null,
            )
        }
    }

    @Test
    fun `refreshTr069FromGateway fills deviceId even when TR-069 already COMPLETE`() {
        val acsSync = mockk<com.dscorp.wispadmin.wispadmin.service.genieacs.SubscriptionAcsSyncService>(relaxed = true)
        val wired = SubscriptionProvisionService(
            repository = repository,
            networkDeviceRepository = networkDeviceRepository,
            planRepository = planRepository,
            placeRepository = placeRepository,
            installationStrategyFactory = installationStrategyFactory,
            errorLogRepository = errorLogRepository,
            gatewayActivation = gatewayActivation,
            pppoeAccessService = pppoeAccessService,
            cpeEnabled = false,
            acsSyncService = acsSync,
        )
        val subscription = baseSubscription().apply {
            id = 3
            installationType = InstallationType.FIBER
            fiberOnuSn = "VSOL0031C0B6"
            oltProvisionStatus = OltProvisionStatus.COMPLETE
            tr069ProvisionStatus = Tr069ProvisionStatus.COMPLETE
            tr069DeviceId = null
        }
        every { gatewayActivation.ifAvailable } returns gatewayClient
        every { gatewayClient.activationBySn("VSOL0031C0B6") } returns GatewayOnuActivateResponse(
            sn = "VSOL0031C0B6",
            oltStatus = "COMPLETE",
            cpeStatus = "COMPLETE",
            deviceId = "B46415-V2804AX15T-12345B4641531C0B6",
        )
        every { repository.save(subscription) } returns subscription

        val result = wired.refreshTr069FromGateway(subscription)

        assertEquals("B46415-V2804AX15T-12345B4641531C0B6", result.tr069DeviceId)
        verify(exactly = 1) {
            acsSync.upsertFromProvision(3, any(), "VSOL0031C0B6")
        }
    }

    @Test
    fun `refreshTr069FromGateway backfills subscription_acs when COMPLETE already has deviceId`() {
        val acsSync = mockk<com.dscorp.wispadmin.wispadmin.service.genieacs.SubscriptionAcsSyncService>(relaxed = true)
        val tagger = mockk<com.dscorp.wispadmin.wispadmin.service.genieacs.GenieAcsSubscriptionTagger>(relaxed = true)
        val wired = SubscriptionProvisionService(
            repository = repository,
            networkDeviceRepository = networkDeviceRepository,
            planRepository = planRepository,
            placeRepository = placeRepository,
            installationStrategyFactory = installationStrategyFactory,
            errorLogRepository = errorLogRepository,
            gatewayActivation = gatewayActivation,
            pppoeAccessService = pppoeAccessService,
            cpeEnabled = false,
            acsSyncService = acsSync,
            acsTagger = tagger,
        )
        val subscription = baseSubscription().apply {
            id = 6
            installationType = InstallationType.FIBER
            fiberOnuSn = "VSOL0031C0B6"
            oltProvisionStatus = OltProvisionStatus.COMPLETE
            tr069ProvisionStatus = Tr069ProvisionStatus.COMPLETE
            tr069DeviceId = "B46415-V2804AX15T-12345B4641531C0B6"
        }

        wired.refreshTr069FromGateway(subscription)

        verify(exactly = 0) { gatewayClient.activationBySn(any()) }
        verify {
            acsSync.upsertFromProvision(
                6,
                match {
                    it.deviceId == "B46415-V2804AX15T-12345B4641531C0B6" &&
                        it.status == Tr069ProvisionStatus.COMPLETE
                },
                "VSOL0031C0B6",
            )
        }
        verify {
            tagger.apply(
                deviceId = "B46415-V2804AX15T-12345B4641531C0B6",
                subscriptionId = 6,
                kind = com.dscorp.wispadmin.wispadmin.service.genieacs.GenieAcsServiceKind.INTERNET,
                fullName = "Ana Lopez",
                previousDeviceId = null,
            )
        }
    }

    @Test
    fun `retryTr069 reapplies provision when MANUAL_REQUIRED and OLT COMPLETE`() {
        val subscription = baseSubscription().apply {
            id = 42
            installationType = InstallationType.FIBER
            fiberOnuSn = "ALCL123"
            oltProvisionStatus = OltProvisionStatus.COMPLETE
            tr069ProvisionStatus = Tr069ProvisionStatus.MANUAL_REQUIRED
            vlan = "100"
            ip = "192.168.30.10"
            pppoeUsername = "gf42"
            accessMode = com.dscorp.wispadmin.wispadmin.data.model.AccessMode.PPPOE_DYNAMIC
        }
        every { repository.findById(42) } returns Optional.of(subscription)
        every { repository.save(subscription) } returns subscription
        every { gatewayActivation.ifAvailable } returns gatewayClient
        every { pppoeAccessService.decryptedPassword(subscription) } returns "secreto123"
        every { gatewayClient.activationBySn("ALCL123") } returns GatewayOnuActivateResponse(
            uniqueExternalId = "gigafiber-ma5608t_1_6_46",
            sn = "ALCL123",
            oltStatus = "COMPLETE",
            cpeStatus = "MANUAL_REQUIRED",
        )
        every { gatewayClient.provision(any()) } returns com.dscorp.wispadmin.wispadmin.oltclient.GatewayCpeProvisionResponse(
            sn = "ALCL123",
            status = "COMPLETE",
        )

        val result = service.retryTr069(42)

        assertEquals(Tr069ProvisionStatus.COMPLETE, result.tr069ProvisionStatus)
        verify {
            gatewayClient.provision(
                match {
                    it.sn == "ALCL123" &&
                        it.uniqueExternalId == "gigafiber-ma5608t_1_6_46" &&
                        it.pppoeUsername == "gf42" &&
                        it.pppoePassword == "secreto123" &&
                        it.wanVlanId == 100
                }
            )
        }
        verify(exactly = 1) { gatewayClient.activationBySn("ALCL123") }
    }

    @Test
    fun `retryTr069 returns current dto without reapplying when already COMPLETE`() {
        val subscription = baseSubscription().apply {
            id = 42
            installationType = InstallationType.FIBER
            oltProvisionStatus = OltProvisionStatus.COMPLETE
            tr069ProvisionStatus = Tr069ProvisionStatus.COMPLETE
        }
        every { repository.findById(42) } returns Optional.of(subscription)

        val result = service.retryTr069(42)

        assertEquals(Tr069ProvisionStatus.COMPLETE, result.tr069ProvisionStatus)
        verify(exactly = 0) { gatewayClient.provision(any()) }
    }

    @Test
    fun `retryTr069 throws when OLT is not COMPLETE`() {
        val subscription = baseSubscription().apply {
            id = 42
            installationType = InstallationType.FIBER
            oltProvisionStatus = OltProvisionStatus.PENDING
            tr069ProvisionStatus = Tr069ProvisionStatus.MANUAL_REQUIRED
        }
        every { repository.findById(42) } returns Optional.of(subscription)

        assertThrows(IllegalStateException::class.java) {
            service.retryTr069(42)
        }
        verify(exactly = 0) { gatewayClient.activationBySn(any()) }
    }

    @Test
    fun `retryTr069 applies for ONLY_TV with ONU`() {
        val subscription = baseSubscription().apply {
            id = 42
            installationType = InstallationType.ONLY_TV_FIBER
            fiberOnuSn = "VSOL0031C0B6"
            oltProvisionStatus = OltProvisionStatus.COMPLETE
            tr069ProvisionStatus = Tr069ProvisionStatus.PENDING
            vlan = "100"
        }
        every { repository.findById(42) } returns Optional.of(subscription)
        every { repository.save(subscription) } returns subscription
        every { gatewayActivation.ifAvailable } returns gatewayClient
        every { gatewayClient.provision(any()) } returns com.dscorp.wispadmin.wispadmin.oltclient.GatewayCpeProvisionResponse(
            sn = "VSOL0031C0B6",
            status = "COMPLETE",
        )

        val result = service.retryTr069(42)

        assertEquals(Tr069ProvisionStatus.COMPLETE, result.tr069ProvisionStatus)
        verify(exactly = 1) { gatewayClient.provision(any()) }
    }

    @Test
    fun `retryTr069 throws for ONLY_TV without ONU`() {
        val subscription = baseSubscription().apply {
            id = 42
            installationType = InstallationType.ONLY_TV_FIBER
            fiberOnuSn = null
            oltProvisionStatus = OltProvisionStatus.NA
            tr069ProvisionStatus = Tr069ProvisionStatus.NA
        }
        every { repository.findById(42) } returns Optional.of(subscription)

        assertThrows(IllegalStateException::class.java) {
            service.retryTr069(42)
        }
        verify(exactly = 0) { gatewayClient.activationBySn(any()) }
    }

    @Test
    fun `retryTr069 throws when subscription missing`() {
        every { repository.findById(99) } returns Optional.empty()

        assertThrows(NoSuchElementException::class.java) {
            service.retryTr069(99)
        }
    }

    private fun baseSubscription() = Subscription(
        firstName = "Ana",
        lastName = "Lopez",
        dni = "87654321",
        equipmentCondition = EquipmentCondition.LOAN
    ).apply {
        installationType = InstallationType.WIRELESS
    }

    private fun sampleRequest() = SubscriptionRequest(
        firstName = "Ana",
        lastName = "Lopez",
        dni = "87654321",
        address = "Calle 1",
        phone = "999",
        subscriptionDate = System.currentTimeMillis(),
        planId = 1,
        additionalDeviceIds = emptyList(),
        placeId = 1,
        location = GeoLocation(-11.0, -77.0),
        technicianId = 1,
        hostDeviceId = 1,
        installationType = InstallationType.WIRELESS,
        equipmentCondition = EquipmentCondition.LOAN
    )
}
