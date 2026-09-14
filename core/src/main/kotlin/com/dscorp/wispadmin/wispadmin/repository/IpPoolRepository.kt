package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.IpPool
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface IpPoolRepository : JpaRepository<IpPool, Int> {

    @Query("SELECT s.ip FROM Subscription s WHERE s.ipPool.id = :id")
    fun getIps(id: Int): List<String>

    @Query("SELECT ip FROM IpPool ip WHERE ip.isEligible = true")
    fun findAllEligiblePools(): List<IpPool>

}