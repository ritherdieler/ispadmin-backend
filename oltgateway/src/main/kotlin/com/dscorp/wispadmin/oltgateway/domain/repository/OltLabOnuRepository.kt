package com.dscorp.wispadmin.oltgateway.domain.repository

import com.dscorp.wispadmin.oltgateway.domain.entity.OltLabOnu
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OltLabOnuRepository : JpaRepository<OltLabOnu, String>
