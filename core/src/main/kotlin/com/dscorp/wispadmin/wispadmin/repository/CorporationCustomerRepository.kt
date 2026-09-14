package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.CorporateClient
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CorporationCustomerRepository : JpaRepository<CorporateClient, Int> {
    @Query("SELECT COALESCE(SUM(c.invoicedAmount), 0) FROM CorporateClient c WHERE c.active = true")
    fun sumActiveInvoicedAmount(): Double
}
