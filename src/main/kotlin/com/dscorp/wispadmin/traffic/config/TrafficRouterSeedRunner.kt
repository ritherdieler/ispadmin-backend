package com.dscorp.wispadmin.traffic.config

import com.dscorp.wispadmin.traffic.client.HttpTrafficRouterDirectoryClient
import com.dscorp.wispadmin.traffic.entity.TrafficRouter
import com.dscorp.wispadmin.traffic.repository.TrafficRouterRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TrafficRouterSeedRunner(
    private val routers: TrafficRouterRepository,
    private val properties: TrafficProperties,
    private val directory: HttpTrafficRouterDirectoryClient,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) = synchronize()

    @Scheduled(fixedDelayString="\${traffic.router-directory-interval-ms:60000}",initialDelayString="\${traffic.router-directory-interval-ms:60000}")
    fun synchronize() {
        if (!properties.routerSeed.enabled) return
        try {
            val remote=directory.list()
            val ids=remote.map { it.id }.toSet()
            val removed=routers.findAll().filter { it.id !in ids }.map { it.copy(enabled=false) }
            routers.saveAll(removed + remote.map { TrafficRouter(it.id,it.name,it.host,it.username,it.password,it.enabled) })
        } catch (ex: Exception) {
            LoggerFactory.getLogger(javaClass).warn("Router directory refresh failed; preserving local inventory ({})",ex.javaClass.simpleName)
        }
    }
}
