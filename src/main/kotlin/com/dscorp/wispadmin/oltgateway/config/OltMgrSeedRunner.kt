package com.dscorp.wispadmin.oltgateway.config

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOltModel
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltModelRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class OltMgrSeedRunner(
    private val oltRepository: OltMgrOltRepository,
    private val oltModelRepository: OltMgrOltModelRepository,
    private val properties: OltGatewayProperties
) : ApplicationRunner {

    companion object {
        private val logger = LoggerFactory.getLogger(OltMgrSeedRunner::class.java)
    }

    override fun run(args: ApplicationArguments?) {
        val model = upsertModel()
        val existing = oltRepository.findByName(properties.oltId)
        if (existing.isPresent) {
            val olt = existing.get()
            if (olt.model == null) {
                olt.model = model
                olt.updatedAt = Instant.now()
                oltRepository.save(olt)
                logger.info("Backfilled olt_mgr_olt model={} name={}", model.code, properties.oltId)
            }
            return
        }
        val now = Instant.now()
        oltRepository.save(
            OltMgrOlt(
                name = properties.oltId,
                ipAddress = properties.host,
                mgmtProtocol = "ssh",
                mgmtPort = properties.port,
                usernameEnc = properties.username,
                passwordEnc = properties.password,
                model = model,
                hardwareVersion = "${model.vendor}-${model.product}",
                enabled = true,
                createdAt = now,
                updatedAt = now
            )
        )
        logger.info("Seeded olt_mgr_olt name={} model={}", properties.oltId, model.code)
    }

    private fun upsertModel(): OltMgrOltModel {
        val code = properties.modelCode.ifBlank { "MA5608T" }
        val existing = oltModelRepository.findByCode(code)
        if (existing.isPresent) {
            return existing.get()
        }
        val created = oltModelRepository.save(
            OltMgrOltModel(
                code = code,
                vendor = "Huawei",
                product = code,
                family = "MA5600T",
                maxConcurrentCliSessions = 4,
                maxSlotProbe = properties.inventory.maxSlotProbe,
                defaultPortsPerGponBoard = properties.inventory.defaultPortsPerGponBoard,
                notes = "Exclusive gateway CLI user; use all max_concurrent_cli_sessions for inventory reads"
            )
        )
        logger.info(
            "Seeded olt_mgr_olt_model code={} maxConcurrentCliSessions={}",
            created.code,
            created.maxConcurrentCliSessions
        )
        return created
    }
}
