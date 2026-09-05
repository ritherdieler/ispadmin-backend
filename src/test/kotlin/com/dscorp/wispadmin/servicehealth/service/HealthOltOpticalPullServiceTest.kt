package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.client.HealthOltGatewayHttpClient
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.port.HealthOltIngestPort
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider

class HealthOltOpticalPullServiceTest {

    @Test
    fun `no ingesta si optical esta apagado o el gateway no tiene URL`() {
        val ingest = mockk<HealthOltIngestPort>(relaxed = true)
        val provider = mockk<ObjectProvider<HealthOltIngestPort>>()
        io.mockk.every { provider.ifAvailable } returns ingest
        val subscriptions = mockk<ObjectProvider<com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository>>()
        io.mockk.every { subscriptions.ifAvailable } returns null
        val gateway = HealthOltGatewayHttpClient(baseUrl = "", apiKey = "k", subscriptions = subscriptions)
        val properties = ServiceHealthProperties().apply {
            enabled = true
            opticalEnabled = true
        }
        HealthOltOpticalPullService(properties, gateway, provider).pull()
        verify(exactly = 0) { ingest.onOptical(any()) }
        verify(exactly = 0) { ingest.onState(any(), any(), any(), any()) }
    }

    @Test
    fun `no llama al gateway si health esta deshabilitado`() {
        val ingest = mockk<HealthOltIngestPort>(relaxed = true)
        val provider = mockk<ObjectProvider<HealthOltIngestPort>>()
        io.mockk.every { provider.ifAvailable } returns ingest
        val gateway = mockk<HealthOltGatewayHttpClient>(relaxed = true)
        val properties = ServiceHealthProperties().apply {
            enabled = false
            opticalEnabled = true
        }
        HealthOltOpticalPullService(properties, gateway, provider).pull()
        verify(exactly = 0) { gateway.pullOptical() }
    }
}
