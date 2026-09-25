package com.dscorp.wispadmin.acs.config

import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.dscorp.wispadmin.acs.service.AcsPurgeException
import com.dscorp.wispadmin.acs.service.AcsSubscriptionPurgeService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.ResourceAccessException
import javax.sql.DataSource

@Configuration
@ConditionalOnProperty(prefix = "gigafiber.subsystems.acs", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("'\${acs.datasource.url:}'.trim().length() > 0")
class AcsSubscriptionPurgeConfig {
    @Bean
    fun acsSubscriptionPurgeService(
        @Qualifier("acsDataSource") source: DataSource,
        client: GenieAcsClient,
    ): AcsSubscriptionPurgeService {
        val jdbc = JdbcTemplate(source)
        return AcsSubscriptionPurgeService(
            deleteTasks = { sn -> jdbc.update("DELETE FROM acs_onboarding_v2_task WHERE sn = ?", sn) },
            deleteCpe = { sn -> jdbc.update("DELETE FROM cpe_record WHERE sn = ?", sn) },
            purgeDevice = { deviceId ->
                try {
                    client.forgetDevice(deviceId)
                } catch (ex: HttpStatusCodeException) {
                    throw AcsPurgeException("ACS HTTP ${ex.statusCode.value()}", ex.statusCode.is5xxServerError)
                } catch (ex: ResourceAccessException) {
                    throw AcsPurgeException(ex.message ?: "ACS sin respuesta", true)
                }
            },
        )
    }
}
