package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.Place
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PlaceRepository : JpaRepository<Place, Int> {

    @Query(
        """
        SELECT * 
        FROM place p
        WHERE ST_Contains(
            p.area, 
            ST_GeomFromText(
                CONCAT('POINT(', :longitude, ' ', :latitude, ')'),
                4326
            )
        )
        LIMIT 1
    """,
        nativeQuery = true
    )
    fun findPlaceContainingPoint(
        @Param("latitude") latitude: Double,
        @Param("longitude") longitude: Double
    ): Place?

}