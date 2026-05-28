package com.dscorp.wispadmin.wispadmin.data.model

import javax.persistence.*

@Entity
@Table(name = "plan")
data class Plan(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    var id: Int,
    var name: String? = null,
    var price: Double? = null,
    var downloadSpeed: Int? = null,
    var uploadSpeed: Int? = null,
    @Enumerated(EnumType.STRING)
    var type: InstallationType? = null,
    var isActive: Boolean = true,
) {

    fun getTag(): String {

        val tag: String = if (type == InstallationType.WIRELESS) {
            "W"
        } else {
            "F"
        }

        return "${tag}-$name-${downloadSpeed}MB"
    }


}