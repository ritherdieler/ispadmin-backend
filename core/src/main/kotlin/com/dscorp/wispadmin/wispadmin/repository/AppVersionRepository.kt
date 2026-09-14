package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.AppVersion
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import org.springframework.data.jpa.repository.JpaRepository

interface AppVersionRepository : JpaRepository<AppVersion, Int> {

}