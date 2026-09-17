package com.dscorp.wispadmin.netdiag.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class NetDiagMikrotikClientWiringTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(NetDiagConfig::class.java)
        .withBean(ObjectMapper::class.java)
        .withPropertyValues(
            "net.diag.enabled=true",
            "net.diag.api-key=test-netdiag-key"
        )

    @Test
    fun `netdiag does not open a direct MikroTik client`() {
        contextRunner.run { context ->
            assertThat(context).doesNotHaveBean("netDiagMikrotikClient")
        }
    }
}
