package com.dscorp.wispadmin.wispadmin.data.model

import org.hibernate.annotations.Type
import org.locationtech.jts.geom.Polygon
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
data class Place(
    @Id
    @GeneratedValue
    var id: Int,
    val name: String? = null,
    val latitude: Float? = null,
    val longitude: Float? = null,

    @Column(columnDefinition = "POLYGON SRID 4326")
    val area: Polygon? = null
)