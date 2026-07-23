package com.dscorp.wispadmin.wispadmin.smartmap

import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import kotlin.math.abs

object ClientLocationRules {
    const val DEFAULT_LAT = -11.233708
    const val DEFAULT_LNG = -77.376278

    private const val COORDINATE_EPSILON = 1e-5

    fun isDefaultClientCoordinate(latitude: Double, longitude: Double): Boolean {
        return abs(latitude - DEFAULT_LAT) <= COORDINATE_EPSILON &&
            abs(longitude - DEFAULT_LNG) <= COORDINATE_EPSILON
    }

    fun hasRealClientGps(location: GeoLocation?): Boolean {
        if (location == null) {
            return false
        }
        if (location.latitude == 0.0 && location.longitude == 0.0) {
            return false
        }
        return !isDefaultClientCoordinate(location.latitude, location.longitude)
    }
}
