package com.dscorp.wispadmin.wispadmin.service.genieacs

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GenieAcsSubscriptionTaggerTest {

    private val client = mockk<GenieAcsClient>()
    private lateinit var tagger: GenieAcsSubscriptionTagger

    @BeforeEach
    fun setUp() {
        tagger = GenieAcsSubscriptionTagger(client)
        every { client.addTag(any(), any()) } returns true
        every { client.deleteTag(any(), any()) } returns true
        every { client.listTags(any()) } returns emptyList()
    }

    @Test
    fun `apply adds sub t and c tags`() {
        tagger.apply(
            deviceId = "dev-1",
            subscriptionId = 744,
            kind = GenieAcsServiceKind.INTERNET,
            fullName = "Juan Perez",
            previousDeviceId = null,
        )

        verify { client.addTag("dev-1", "sub-744") }
        verify { client.addTag("dev-1", "t:INTERNET") }
        verify { client.addTag("dev-1", "c:JUAN PEREZ") }
    }

    @Test
    fun `apply removes stale managed tags on current device`() {
        every { client.listTags("dev-1") } returns listOf("sub-10", "t:TV", "c:OLD", "lab")

        tagger.apply(
            deviceId = "dev-1",
            subscriptionId = 744,
            kind = GenieAcsServiceKind.DUO,
            fullName = "Juan Perez",
            previousDeviceId = null,
        )

        verify { client.deleteTag("dev-1", "sub-10") }
        verify { client.deleteTag("dev-1", "t:TV") }
        verify { client.deleteTag("dev-1", "c:OLD") }
        verify(exactly = 0) { client.deleteTag("dev-1", "lab") }
    }

    @Test
    fun `apply clears managed tags from previous device`() {
        every { client.listTags("old-dev") } returns listOf("sub-744", "t:INTERNET", "c:JUAN PEREZ", "lab")
        every { client.listTags("dev-1") } returns emptyList()

        tagger.apply(
            deviceId = "dev-1",
            subscriptionId = 744,
            kind = GenieAcsServiceKind.INTERNET,
            fullName = "Juan Perez",
            previousDeviceId = "old-dev",
        )

        verify { client.deleteTag("old-dev", "sub-744") }
        verify { client.deleteTag("old-dev", "t:INTERNET") }
        verify { client.deleteTag("old-dev", "c:JUAN PEREZ") }
        verify(exactly = 0) { client.deleteTag("old-dev", "lab") }
        verify { client.addTag("dev-1", "sub-744") }
    }
}
