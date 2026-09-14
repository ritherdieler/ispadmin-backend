package com.dscorp.wispadmin.wispadmin.data.model

import org.hibernate.collection.internal.PersistentBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import javax.persistence.FetchType
import javax.persistence.JoinColumn
import javax.persistence.ManyToMany
import javax.persistence.OneToOne

class SubscriptionFetchStrategyTest {

    private fun oneToOneFetch(field: String): FetchType =
        Subscription::class.java.getDeclaredField(field)
            .getAnnotation(OneToOne::class.java)
            .fetch

    private fun manyToManyFetch(field: String): FetchType =
        Subscription::class.java.getDeclaredField(field)
            .getAnnotation(ManyToMany::class.java)
            .fetch

    @Test
    fun `las relaciones que ningun listado proyecta se cargan a demanda`() {
        listOf("ipPool", "cpe", "coupon").forEach { field ->
            assertEquals(FetchType.LAZY, oneToOneFetch(field), "relacion $field")
        }
    }

    @Test
    fun `los equipos adicionales dejan de traerse en cada consulta de suscripcion`() {
        assertEquals(FetchType.LAZY, manyToManyFetch("additionalDevices"))
    }

    @Test
    fun `el DTO omite los equipos adicionales cuando la coleccion no esta inicializada`() {
        @Suppress("UNCHECKED_CAST")
        val uninitialized = PersistentBag(null) as List<NetworkDevice>
        val subscription = Subscription(equipmentCondition = EquipmentCondition.LOAN)
            .apply { additionalDevices = uninitialized }

        assertNull(subscription.toDto().additionalDevices)
    }

    @Test
    fun `el vinculo de ONU es solo el serial en subscription`() {
        val field = Subscription::class.java.getDeclaredField("fiberOnuSn")
        val column = field.getAnnotation(javax.persistence.Column::class.java)
        assertEquals("fiber_onu_sn", column.name)
        assertNull(field.getAnnotation(javax.persistence.JoinColumn::class.java))
        assertNull(field.getAnnotation(OneToOne::class.java))
    }

    @Test
    fun `las relaciones que el DTO de listado proyecta siguen resolviendose sin lazy loading`() {
        listOf("plan", "place", "napBox", "technician", "hostDevice").forEach { field ->
            assertEquals(FetchType.EAGER, oneToOneFetch(field), "relacion $field")
        }
    }
}
