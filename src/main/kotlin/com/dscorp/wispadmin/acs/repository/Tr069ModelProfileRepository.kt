package com.dscorp.wispadmin.acs.repository

import com.dscorp.wispadmin.acs.entity.Tr069ModelProfileRecord
import org.springframework.data.jpa.repository.JpaRepository

interface Tr069ModelProfileRepository : JpaRepository<Tr069ModelProfileRecord, String>
