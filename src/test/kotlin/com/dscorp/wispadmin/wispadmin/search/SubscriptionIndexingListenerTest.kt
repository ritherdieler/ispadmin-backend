package com.dscorp.wispadmin.wispadmin.search

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.search.api.SearchIndexer
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchableDocument
import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionChangedEvent
import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionDeletedEvent
import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionDocumentMapper
import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionIndexingListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional

class SubscriptionIndexingListenerTest {

    private val searchIndexer = mockk<SearchIndexer>(relaxed = true)
    private val repository = mockk<SubscriptionRepository>()
    private val mapper = SubscriptionDocumentMapper()

    private val listener = SubscriptionIndexingListener(searchIndexer, repository, mapper)

    @Test
    fun `indexa la suscripcion cuando se recibe un evento de cambio`() {
        val subscription = Subscription(id = 7, firstName = "Ana", lastName = "Perez", serviceStatus = ServiceStatus.ACTIVE, equipmentCondition = EquipmentCondition.LOAN)
        every { repository.findById(7) } returns Optional.of(subscription)

        listener.onSubscriptionChanged(SubscriptionChangedEvent(7))

        verify { searchIndexer.index(match<SearchableDocument> { it.id == "7" }) }
    }

    @Test
    fun `elimina del indice cuando se recibe un evento de borrado`() {
        listener.onSubscriptionDeleted(SubscriptionDeletedEvent(9))

        verify { searchIndexer.delete("9") }
    }

    @Test
    fun `no propaga la excepcion cuando el indexador falla`() {
        val subscription = Subscription(id = 3, firstName = "Luis", lastName = "Diaz", serviceStatus = ServiceStatus.ACTIVE, equipmentCondition = EquipmentCondition.LOAN)
        every { repository.findById(3) } returns Optional.of(subscription)
        every { searchIndexer.index(any()) } throws RuntimeException("meili caido")

        listener.onSubscriptionChanged(SubscriptionChangedEvent(3))
    }
}
