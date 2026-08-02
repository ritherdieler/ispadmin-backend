package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.CrmIntegrationSetting
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface CrmIntegrationSettingRepository : JpaRepository<CrmIntegrationSetting, Long> {
    fun findBySettingKey(settingKey: String): Optional<CrmIntegrationSetting>
}
