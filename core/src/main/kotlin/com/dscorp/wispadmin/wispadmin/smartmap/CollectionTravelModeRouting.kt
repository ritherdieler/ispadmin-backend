package com.dscorp.wispadmin.wispadmin.smartmap

object CollectionTravelModeRouting {
    fun resolveMapboxProfile(
        travelMode: CollectionTravelMode,
        configuredVehicleProfile: String,
    ): String = when (travelMode) {
        CollectionTravelMode.VEHICLE -> configuredVehicleProfile.trim().ifBlank { "mapbox/driving-traffic" }
        CollectionTravelMode.WALKING -> "mapbox/walking"
    }

    fun resolveDirectionsAnnotations(travelMode: CollectionTravelMode, configuredAnnotations: String): String =
        when (travelMode) {
            CollectionTravelMode.VEHICLE -> configuredAnnotations.trim().ifBlank { "duration,congestion" }
            CollectionTravelMode.WALKING -> "duration"
        }

    fun shouldUseCurbApproaches(travelMode: CollectionTravelMode): Boolean =
        travelMode == CollectionTravelMode.VEHICLE

    fun resolveAvoidManeuverRadiusMeters(
        travelMode: CollectionTravelMode,
        requestedRadius: Int?,
        configuredDefaultRadius: Int,
        inMotion: Boolean,
    ): Int? {
        if (travelMode != CollectionTravelMode.VEHICLE) {
            return null
        }
        return when {
            requestedRadius != null && requestedRadius > 0 -> requestedRadius
            inMotion -> configuredDefaultRadius
            else -> null
        }
    }
}
