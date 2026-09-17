package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.traffic.dto.RouterOsAddRequest
import com.dscorp.wispadmin.traffic.dto.RouterOsCallRequest
import com.dscorp.wispadmin.traffic.dto.RouterOsPrintRequest
import com.dscorp.wispadmin.traffic.dto.RouterOsRemoveRequest
import com.dscorp.wispadmin.traffic.dto.RouterOsSetRequest
import com.dscorp.wispadmin.traffic.repository.TrafficRouterRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class RouterOsCommandUseCase(
    private val routerRepository: TrafficRouterRepository,
    @Qualifier("trafficPollMikrotikClient")
    private val mikrotikClient: MikrotikClient,
    private val routerOsClientProperties: RouterOsClientProperties,
) {
    fun print(hostDeviceId: Int, request: RouterOsPrintRequest): Result<List<Map<String, String>>> = runCatching {
        RouterOsPathAllowlist.requireAllowed(request.path)
        withSession(hostDeviceId) { session ->
            session.print(request.path, request.query, request.proplist)
        }
    }

    fun add(hostDeviceId: Int, request: RouterOsAddRequest): Result<Unit> = runCatching {
        RouterOsPathAllowlist.requireAllowed(request.path)
        withSession(hostDeviceId) { session ->
            session.add(request.path, request.args)
        }
    }

    fun set(hostDeviceId: Int, request: RouterOsSetRequest): Result<Unit> = runCatching {
        RouterOsPathAllowlist.requireAllowed(request.path)
        withSession(hostDeviceId) { session ->
            session.set(request.path, request.id, request.args)
        }
    }

    fun remove(hostDeviceId: Int, request: RouterOsRemoveRequest): Result<Unit> = runCatching {
        RouterOsPathAllowlist.requireAllowed(request.path)
        withSession(hostDeviceId) { session ->
            session.remove(request.path, request.id)
        }
    }

    fun call(hostDeviceId: Int, request: RouterOsCallRequest): Result<List<Map<String, String>>> = runCatching {
        RouterOsPathAllowlist.requireAllowed(request.path)
        withSession(hostDeviceId) { session ->
            session.call(request.path, request.args)
        }
    }

    private fun <T> withSession(hostDeviceId: Int, block: (MikrotikSession) -> T): T {
        val router = routerRepository.findById(hostDeviceId).orElse(null)
            ?: throw IllegalArgumentException("TrafficRouter $hostDeviceId not found")
        val deviceRef = MikrotikDeviceRef(
            id = router.id.toString(),
            host = router.host,
            port = routerOsClientProperties.rest.port,
            username = router.username,
            password = router.password,
        )
        return mikrotikClient.withSession(deviceRef, block)
    }
}
