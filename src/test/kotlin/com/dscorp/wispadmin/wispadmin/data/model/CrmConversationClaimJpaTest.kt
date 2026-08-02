package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.repository.CrmConversationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:crm_claim;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
    ]
)
class CrmConversationClaimJpaTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = [CrmConversation::class])
    @EnableJpaRepositories(basePackageClasses = [CrmConversationRepository::class])
    class TestApp

    @Autowired
    private lateinit var conversationRepository: CrmConversationRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `atomic claim allows only one agent under concurrency`() {
        val conversationId = transactionTemplate.execute {
            val saved = conversationRepository.saveAndFlush(
                CrmConversation(
                    channel = CrmChannel.WHATSAPP,
                    phone = "51988887777",
                    status = CrmConversationStatus.PENDING,
                    lastInboundAt = LocalDateTime.now()
                )
            )
            requireNotNull(saved.id)
        }!!

        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val successes = AtomicInteger(0)

        listOf(101, 202).forEach { agentId ->
            pool.submit {
                start.await()
                try {
                    val updated = transactionTemplate.execute {
                        conversationRepository.claimIfUnassigned(
                            id = conversationId,
                            agentId = agentId,
                            claimedAt = LocalDateTime.now()
                        )
                    } ?: 0
                    if (updated == 1) successes.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }

        start.countDown()
        done.await(5, TimeUnit.SECONDS)
        pool.shutdownNow()

        assertEquals(1, successes.get())
        val claimed = transactionTemplate.execute {
            conversationRepository.findById(conversationId).orElseThrow()
        }!!
        assertEquals(CrmConversationStatus.ASSIGNED, claimed.status)
        assertEquals(true, claimed.assignedAgentId == 101 || claimed.assignedAgentId == 202)

        transactionTemplate.execute {
            conversationRepository.deleteById(conversationId)
            null
        }
    }
}
