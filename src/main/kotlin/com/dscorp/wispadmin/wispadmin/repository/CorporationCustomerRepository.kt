package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.CorporateClient
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CorporationCustomerRepository : JpaRepository<CorporateClient, Int>

