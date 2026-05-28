package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import org.springframework.data.jpa.repository.JpaRepository

interface NapBoxRepository : JpaRepository<NapBox, Int>