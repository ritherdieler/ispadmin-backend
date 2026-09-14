package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.charset.StandardCharsets
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import kotlin.concurrent.thread

@Component
@Profile("!staging")
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class NetDiagSnmpTrapUdpListener(
    private val properties: NetDiagProperties,
    private val trapIngestService: NetDiagSnmpTrapIngestService,
    private val targetRepository: NetDiagTargetRepository
) {

    private val logger = LoggerFactory.getLogger(NetDiagSnmpTrapUdpListener::class.java)
    @Volatile
    private var running = false
    private var worker: Thread? = null
    private var socket: DatagramSocket? = null

    @PostConstruct
    fun start() {
        if (!properties.snmp.trap.udpEnabled) {
            return
        }
        running = true
        worker = thread(name = "netdiag-snmp-trap-udp", isDaemon = true) {
            try {
                DatagramSocket(properties.snmp.trap.udpPort).use { datagramSocket ->
                    socket = datagramSocket
                    logger.info("NetDiag SNMP trap UDP listening on {}", properties.snmp.trap.udpPort)
                    val buffer = ByteArray(8192)
                    while (running) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        datagramSocket.receive(packet)
                        val payload = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                        val host = packet.address?.hostAddress
                        val targetId = resolveTargetId(host)
                        runCatching { trapIngestService.ingestUdp(payload, targetId) }
                            .onFailure { logger.warn("SNMP trap ingest failed: {}", it.message) }
                    }
                }
            } catch (ex: Exception) {
                if (running) {
                    logger.warn("SNMP trap UDP listener stopped: {}", ex.message)
                }
            }
        }
    }

    @PreDestroy
    fun stop() {
        running = false
        socket?.close()
        worker?.interrupt()
    }

    private fun resolveTargetId(sourceHost: String?): Long? {
        if (sourceHost.isNullOrBlank()) return null
        return targetRepository.findByEnabledTrue().firstOrNull { target ->
            target.name.contains(sourceHost, ignoreCase = true) ||
                target.monitorConfig.orEmpty().contains(sourceHost)
        }?.id
    }
}
