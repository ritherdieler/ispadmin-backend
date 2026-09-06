package com.dscorp.wispadmin.wispadmin.trafficclient

import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class TrafficTargetProviderTest {
    @Test fun `cursor endpoint limits database reads and validates pagination`() {
        val repo = mockk<SubscriptionRepository>()
        every { repo.findTrafficTargetsAfter(0, match { it.pageSize == 3 }) } returns
            listOf(Subscription(equipmentCondition=com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition.LOAN, id=1, ip="192.0.2.1"), Subscription(equipmentCondition=com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition.LOAN, id=2, ip="192.0.2.2"), Subscription(equipmentCondition=com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition.LOAN, id=4, ip="192.0.2.4"))
        every { repo.findTrafficTargetsAfter(2, match { it.pageSize == 3 }) } returns
            listOf(Subscription(equipmentCondition=com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition.LOAN, id=4, ip="192.0.2.4"))
        val mvc = MockMvcBuilders.standaloneSetup(TrafficDirectoryController(TrafficDirectoryService(repo))).build()
        mvc.get("/internal/traffic/targets/page?after=0&size=2").andExpect {
            status { isOk() }; jsonPath("$.items.length()") { value(2) }; jsonPath("$.nextCursor") { value(2) }
        }
        mvc.get("/internal/traffic/targets/page?after=2&size=2").andExpect {
            status { isOk() }; jsonPath("$.items[0].subscriptionId") { value(4) }; jsonPath("$.nextCursor") { doesNotExist() }
        }
        mvc.get("/internal/traffic/targets/page?after=-1&size=2").andExpect { status { isBadRequest() } }
        mvc.get("/internal/traffic/targets/page?after=0&size=0").andExpect { status { isBadRequest() } }
        verify(exactly=2) { repo.findTrafficTargetsAfter(any(), any()) }
    }
}
