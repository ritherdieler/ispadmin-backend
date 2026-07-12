package com.dscorp.wispadmin.observability.repository

import com.dscorp.wispadmin.observability.entity.ObsAlertChannel
import org.springframework.data.jpa.repository.JpaRepository

interface ObsAlertChannelRepository : JpaRepository<ObsAlertChannel, Long> {

    fun findAllByOrderByCreatedAtDesc(): List<ObsAlertChannel>
}
