package com.dscorp.wispadmin.observability.repository

import com.dscorp.wispadmin.observability.entity.ObsDeliveryReceipt
import org.springframework.data.jpa.repository.JpaRepository

interface ObsDeliveryReceiptRepository : JpaRepository<ObsDeliveryReceipt, String>
