package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.*
import org.springframework.data.jpa.repository.JpaRepository

interface CouponRepository : JpaRepository<Coupon, Int> {
    fun findByCodeAndExpirationDateGreaterThan(code: String, expirationDate: Long): Coupon?

}