package com.dscorp.wispadmin.wispadmin.trafficclient

import com.dscorp.wispadmin.routeros.port.MikrotikCommandException
import com.dscorp.wispadmin.routeros.port.MikrotikSession

class TrafficRouterOsSession(
    private val useCase: TrafficRouterOsCommandUseCase,
    private val hostDeviceId: Int,
) : MikrotikSession {
    override fun print(
        path: String,
        query: Map<String, String>,
        proplist: List<String>,
    ): List<Map<String, String>> {
        return useCase.print(hostDeviceId, path, query, proplist).getOrThrow()
    }

    override fun call(path: String, args: Map<String, String>): List<Map<String, String>> {
        return useCase.call(hostDeviceId, path, args).getOrThrow()
    }

    override fun add(path: String, args: Map<String, String>) {
        useCase.add(hostDeviceId, path, args).getOrThrow()
    }

    override fun set(path: String, id: String, args: Map<String, String>) {
        useCase.set(hostDeviceId, path, id, args).getOrThrow()
    }

    override fun remove(path: String, id: String) {
        useCase.remove(hostDeviceId, path, id).getOrThrow()
    }

    override fun execute(command: String): List<Map<String, String>> {
        throw MikrotikCommandException(
            "Raw execute is not supported; use print/add/set/remove: $command",
        )
    }
}
