package com.dscorp.wispadmin.wispadmin.search

import com.dscorp.wispadmin.wispadmin.search.api.model.SearchableDocument
import com.dscorp.wispadmin.wispadmin.search.infrastructure.meili.MeiliSearchIndexerAdapter
import com.meilisearch.sdk.Client
import com.meilisearch.sdk.Index
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class MeiliSearchIndexerAdapterTest {

    private val index = mockk<Index>(relaxed = true)
    private val client = mockk<Client>()
    private val adapter = MeiliSearchIndexerAdapter(client, "subscriptions")

    private val document = SearchableDocument(
        id = "1",
        fields = mapOf("id" to 1, "fullName" to "Ana Perez")
    )

    @Test
    fun `indexa el documento como json con la primary key id`() {
        every { client.index("subscriptions") } returns index

        adapter.index(document)

        verify { index.addDocuments(any<String>(), "id") }
    }

    @Test
    fun `elimina el documento por id`() {
        every { client.index("subscriptions") } returns index

        adapter.delete("1")

        verify { index.deleteDocument("1") }
    }

    @Test
    fun `no propaga la excepcion cuando el cliente de meili falla`() {
        every { client.index("subscriptions") } throws RuntimeException("meili caido")

        adapter.index(document)
        adapter.delete("1")
    }
}
