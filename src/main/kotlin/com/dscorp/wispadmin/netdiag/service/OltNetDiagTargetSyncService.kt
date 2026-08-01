package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class OltNetDiagTargetSyncResult(
    val upserted: Int,
    val oltTargets: Int,
    val ponTargets: Int
)

@Service
@Order(50)
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class OltNetDiagTargetSyncService(
    private val targetRepository: NetDiagTargetRepository,
    private val oltRepository: OltMgrOltRepository,
    private val oltGatewayProperties: OltGatewayProperties,
    private val objectMapper: ObjectMapper
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(OltNetDiagTargetSyncService::class.java)

    override fun run(args: ApplicationArguments?) {
        val result = sync()
        if (result.upserted > 0) {
            logger.info(
                "Synced NetDiag OLT targets upserted={} olt={} pon={}",
                result.upserted,
                result.oltTargets,
                result.ponTargets
            )
        }
    }

    @Transactional
    fun sync(): OltNetDiagTargetSyncResult {
        val oltId = oltGatewayProperties.oltId
        val olt = oltRepository.findByName(oltId).orElse(null)
            ?: return OltNetDiagTargetSyncResult(0, 0, 0)
        val oltPk = olt.id ?: return OltNetDiagTargetSyncResult(0, 0, 0)
        val now = Instant.now()
        val oltTarget = upsertOltTarget(oltPk, oltId, oltGatewayProperties.host, now)
        val oltTargetId = oltTarget.id ?: return OltNetDiagTargetSyncResult(0, 0, 0)
        val portsPerBoard = oltGatewayProperties.inventory.defaultPortsPerGponBoard.coerceAtLeast(1)
        var ponCount = 0
        for (board in GPON_BOARDS) {
            for (port in 0 until portsPerBoard) {
                upsertPonTarget(oltPk, oltId, oltTargetId, board, port, now)
                ponCount++
            }
        }
        return OltNetDiagTargetSyncResult(
            upserted = 1 + ponCount,
            oltTargets = 1,
            ponTargets = ponCount
        )
    }

    private fun upsertOltTarget(oltPk: Long, oltId: String, mgmtIp: String, now: Instant): NetDiagTarget {
        val name = oltTargetName(oltId)
        val existing = targetRepository.findByName(name).orElse(null)
        val config = objectMapper.createObjectNode().apply {
            put("kind", NetDiagMonitorConfigSupport.KIND_OLT)
            put("oltId", oltId)
            put("mgmtIp", mgmtIp)
        }
        return if (existing != null) {
            existing.deviceRefId = oltPk
            existing.enabled = true
            existing.monitorConfig = objectMapper.writeValueAsString(config)
            existing.updatedAt = now
            targetRepository.save(existing)
        } else {
            targetRepository.save(
                NetDiagTarget(
                    name = name,
                    deviceRefId = oltPk,
                    enabled = true,
                    pollIntervalMs = 600_000,
                    monitorConfig = objectMapper.writeValueAsString(config),
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    private fun upsertPonTarget(
        oltPk: Long,
        oltId: String,
        parentTargetId: Long,
        board: Int,
        port: Int,
        now: Instant
    ): NetDiagTarget {
        val name = ponTargetName(oltId, board, port)
        val existing = targetRepository.findByName(name).orElse(null)
        val config: ObjectNode = objectMapper.createObjectNode().apply {
            put("kind", NetDiagMonitorConfigSupport.KIND_PON)
            put("oltId", oltId)
            put("board", board)
            put("port", port)
        }
        val deviceRefId = ponDeviceRefId(oltPk, board, port)
        return if (existing != null) {
            existing.deviceRefId = deviceRefId
            existing.parentTargetId = parentTargetId
            existing.enabled = true
            existing.monitorConfig = objectMapper.writeValueAsString(config)
            existing.updatedAt = now
            targetRepository.save(existing)
        } else {
            targetRepository.save(
                NetDiagTarget(
                    name = name,
                    deviceRefId = deviceRefId,
                    parentTargetId = parentTargetId,
                    enabled = true,
                    pollIntervalMs = 600_000,
                    monitorConfig = objectMapper.writeValueAsString(config),
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    companion object {
        val GPON_BOARDS = listOf(0, 1)

        fun oltTargetName(oltId: String): String = "OLT-$oltId"

        fun ponTargetName(oltId: String, board: Int, port: Int): String =
            "PON-$oltId-gpon-$board/$port"

        fun ponDeviceRefId(oltPk: Long, board: Int, port: Int): Long =
            1_000_000L + oltPk * 1_000L + board * 100L + port
    }
}
