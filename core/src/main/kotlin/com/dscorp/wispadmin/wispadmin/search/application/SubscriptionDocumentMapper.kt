package com.dscorp.wispadmin.wispadmin.search.application

import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchableDocument
import org.springframework.stereotype.Component

@Component
class SubscriptionDocumentMapper {

    fun toDocument(subscription: Subscription): SearchableDocument {
        val firstName = subscription.firstName?.trim().orEmpty()
        val lastName = subscription.lastName?.trim().orEmpty()
        val fullName = "$firstName $lastName".trim()
        return SearchableDocument(
            id = subscription.id?.toString().orEmpty(),
            fields = mapOf(
                "id" to subscription.id,
                "firstName" to firstName,
                "lastName" to lastName,
                "fullName" to fullName,
                "dni" to subscription.dni,
                "serviceStatus" to subscription.serviceStatus.name,
                "installationType" to subscription.installationType?.name,
                "placeName" to subscription.place?.name
            )
        )
    }
}
