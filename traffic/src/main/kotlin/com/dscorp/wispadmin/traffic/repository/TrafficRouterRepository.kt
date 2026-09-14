package com.dscorp.wispadmin.traffic.repository

import com.dscorp.wispadmin.traffic.entity.TrafficCounterState
import com.dscorp.wispadmin.traffic.entity.TrafficRouter
import org.springframework.data.jpa.repository.JpaRepository

interface TrafficRouterRepository : JpaRepository<TrafficRouter, Int> {
    fun findByEnabledTrue(): List<TrafficRouter>
}

interface TrafficCounterStateRepository : JpaRepository<TrafficCounterState, String> {
    fun findByHostDeviceId(hostDeviceId: Int): List<TrafficCounterState>
}
