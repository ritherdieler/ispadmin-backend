package com.dscorp.wispadmin.acs.repository

import com.dscorp.wispadmin.acs.entity.CpeRecord
import org.springframework.data.jpa.repository.JpaRepository

interface CpeRecordRepository : JpaRepository<CpeRecord, String> {
    fun findByUniqueExternalId(uniqueExternalId: String): CpeRecord?
    fun findByDeviceId(deviceId: String): CpeRecord?
}
