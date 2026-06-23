package com.dscorp.wispadmin.wispadmin.data.model

import javax.persistence.*

@Entity
@Table(name = "user")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Int = -1,
    var name: String? = null,
    var lastName: String? = null,
    @Column(unique = true)
    var username: String? = null,
    var password: String? = null,
    var verified: Boolean = false,
    var email: String? = null,
    var phone: String? = null,
    var dni: String? = null,
    var bornDate: Long? = null,
    @Enumerated(EnumType.STRING)
    var type: UserType? = null,
    var deviceToken: String? = null,

    @OneToMany(mappedBy = "responsible", fetch = FetchType.LAZY)
    val assistanceTickets: MutableSet<AssistanceTicket> = mutableSetOf(),

    @OneToMany(mappedBy = "responsible")
    val outlays: MutableSet<Outlay> = mutableSetOf(),

    @OneToMany(mappedBy = "user")
    val attendances: List<Attendance>? = null,

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    val faceData: List<Face_data> = emptyList(),

    //@OneToMany(mappedBy = "user")
    //val attendanceLogs: List<AttendanceLog>? = null

) {
    enum class UserType {
        ADMIN, TECHNICIAN, CLIENT, SALES, SECRETARY, ACCOUNTANT
    }
}
