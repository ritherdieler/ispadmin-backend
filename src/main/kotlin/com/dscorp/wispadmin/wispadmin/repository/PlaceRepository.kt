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
        WHERE p.area IS NOT NULL
        AND ST_Contains(
            ST_SRID(p.area, 4326),
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

    @Query(
        """
        SELECT *
        FROM place p
        WHERE LOWER(TRIM(p.name)) = LOWER(TRIM(:placeName))
        AND p.area IS NOT NULL
        AND ST_Contains(
            ST_SRID(p.area, 4326),
            ST_GeomFromText(
                CONCAT('POINT(', :longitude, ' ', :latitude, ')'),
                4326
            )
        )
        LIMIT 1
        """,
        nativeQuery = true
    )
    fun findPlaceContainingPointInNamedPlace(
        @Param("placeName") placeName: String,
        @Param("latitude") latitude: Double,
        @Param("longitude") longitude: Double,
    ): Place?

    @Query(
        """
        SELECT p FROM Place p
        WHERE LOWER(TRIM(p.name)) = LOWER(TRIM(:placeName))
        """
    )
    fun findByNormalizedName(@Param("placeName") placeName: String): List<Place>

    @Query(
        """
        SELECT LOWER(TRIM(p.name)) FROM Place p
        WHERE p.area IS NOT NULL
        """
    )
    fun findNormalizedNamesWithPolygon(): List<String>

    @Query(
        """
        SELECT p FROM Place p
        WHERE p.area IS NOT NULL
          AND p.name IS NOT NULL
          AND TRIM(p.name) <> ''
        ORDER BY LOWER(TRIM(p.name)) ASC
        """
    )
    fun findAllWithPolygonOrderedByName(): List<Place>

}