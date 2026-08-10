package com.dscorp.wispadmin.wispadmin.smartmap

import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.GeoLocationDto

/**
 * Resolves a subscription map pin using real GPS first, then Place fallback.
 */
object SmartMapLocationResolver {

    fun resolve(subscription: Subscription): GeoLocationDto? {
        subscription.location?.let { location ->
            if (ClientLocationRules.hasRealClientGps(location)) {
                return GeoLocationDto(location.latitude, location.longitude)
            }
        }

        val placeLatitude = subscription.place?.latitude?.toDouble()
        val placeLongitude = subscription.place?.longitude?.toDouble()
        if (
            placeLatitude != null &&
            placeLongitude != null &&
            placeLatitude != 0.0 &&
            placeLongitude != 0.0 &&
            !ClientLocationRules.isDefaultClientCoordinate(placeLatitude, placeLongitude)
        ) {
            return GeoLocationDto(placeLatitude, placeLongitude)
        }

        return null
    }
}
