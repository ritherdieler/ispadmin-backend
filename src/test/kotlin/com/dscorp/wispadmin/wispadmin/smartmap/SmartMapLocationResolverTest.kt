package com.dscorp.wispadmin.wispadmin.smartmap

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SmartMapLocationResolverTest {

    @Test
    fun `prefers real client gps over place fallback`() {
        val subscription = Subscription(
            id = 1,
            location = GeoLocation(-11.24, -77.38),
            place = Place(id = 1, name = "Tiw", latitude = -11.20f, longitude = -77.30f),
            equipmentCondition = EquipmentCondition.LOAN,
        )

        val location = SmartMapLocationResolver.resolve(subscription)!!

        assertEquals(-11.24, location.latitude, 1e-6)
        assertEquals(-77.38, location.longitude, 1e-6)
    }

    @Test
    fun `falls back to place coordinates when gps is default`() {
        val subscription = Subscription(
            id = 2,
            location = GeoLocation(ClientLocationRules.DEFAULT_LAT, ClientLocationRules.DEFAULT_LNG),
            place = Place(id = 2, name = "Villa", latitude = -11.25f, longitude = -77.39f),
            equipmentCondition = EquipmentCondition.LOAN,
        )

        val location = SmartMapLocationResolver.resolve(subscription)!!

        assertEquals(-11.25, location.latitude, 1e-6)
        assertEquals(-77.39, location.longitude, 1e-6)
    }

    @Test
    fun `returns null when neither gps nor place is valid`() {
        val subscription = Subscription(
            id = 3,
            location = GeoLocation(0.0, 0.0),
            place = null,
            equipmentCondition = EquipmentCondition.LOAN,
        )

        assertNull(SmartMapLocationResolver.resolve(subscription))
    }
}
