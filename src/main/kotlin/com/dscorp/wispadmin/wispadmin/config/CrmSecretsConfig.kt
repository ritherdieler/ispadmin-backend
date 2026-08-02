package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CrmSecretsConfig {

    @Bean
    fun crmSecretCipher(properties: CrmLlmProperties): CrmSecretCipher {
        val key = properties.masterKey.trim().ifBlank {
            "local-dev-crm-secrets-master-key-change-me"
        }
        return CrmSecretCipher(key)
    }
}
