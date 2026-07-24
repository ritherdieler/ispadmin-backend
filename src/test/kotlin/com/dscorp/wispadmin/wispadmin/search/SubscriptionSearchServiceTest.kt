package com.dscorp.wispadmin.wispadmin.search

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.search.api.SearchEngine
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchHit
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchPage
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchQuery
import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionSearchService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionSearchServiceTest {

    private val searchEngine = mockk<SearchEngine>()
    private val fallbackEngine = mockk<SearchEngine>()
    private val repository = mockk<SubscriptionRepository>()

    private val service = SubscriptionSearchService(searchEngine, fallbackEngine, repository)

    private fun subscription(id: Int, firstName: String) =
        Subscription(id = id, firstName = firstName, lastName = "Perez", serviceStatus = ServiceStatus.ACTIVE, equipmentCondition = EquipmentCondition.LOAN)

    @Test
    fun `devuelve respuesta paginada hidratando los ids en el orden del motor`() {
        every { searchEngine.search(any()) } returns SearchPage(
            items = listOf(SearchHit("2"), SearchHit("1")),
            page = 0,
            size = 20,
            total = 2,
            totalPages = 1
        )
        every { repository.findAllById(listOf(2, 1)) } returns listOf(
            subscription(1, "Ana"),
            subscription(2, "Bruno")
        )

        val result = service.search("perez", null, 0, 20)

        assertEquals(2, result.items.size)
        assertEquals(2, result.items[0].id)
        assertEquals(1, result.items[1].id)
        assertEquals(0, result.page)
        assertEquals(20, result.size)
        assertEquals(2L, result.total)
        assertEquals(1, result.totalPages)
    }

    @Test
    fun `propaga el filtro por estado al motor de busqueda`() {
        val querySlot = slot<SearchQuery>()
        every { searchEngine.search(capture(querySlot)) } returns SearchPage(emptyList(), 0, 20, 0, 0)

        service.search("ana", "ACTIVE", 0, 20)

        assertEquals("ACTIVE", querySlot.captured.filters["serviceStatus"])
    }

    @Test
    fun `usa el motor de fallback cuando el motor principal falla`() {
        every { searchEngine.search(any()) } throws RuntimeException("motor caido")
        every { fallbackEngine.search(any()) } returns SearchPage(
            items = listOf(SearchHit("5")),
            page = 0,
            size = 20,
            total = 1,
            totalPages = 1
        )
        every { repository.findAllById(listOf(5)) } returns listOf(subscription(5, "Carla"))

        val result = service.search("carla", null, 0, 20)

        assertEquals(1, result.items.size)
        assertEquals(5, result.items[0].id)
        verify { fallbackEngine.search(any()) }
    }
}
