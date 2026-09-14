package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.persistence.*

@Entity
@Table(name = "installation_order")
data class InstallationOrder(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int,

    // Datos del cliente
    var customerFirstName: String? = null,
    var customerLastName: String? = null,
    var customerAddress: String? = null,
    var customerPhone: String? = null,
    var customerDni: String? = null,

    // Usuario que crea la orden (vendedor)
    @ManyToOne
    @JoinColumn(name = "seller_id")
    var seller: User? = null, // type == SALES

    // Usuario que asigna la fecha (administrativo/logística)
    @ManyToOne
    @JoinColumn(name = "assigned_by_id")
    var assignedBy: User? = null, // type == SECRETARY ACCOUNTANT or ADMIN

    // Técnico asignado
    @ManyToOne
    @JoinColumn(name = "technician_id")
    var technician: User? = null, // type == TECHNICIAN

    // Lugar de instalación
    @ManyToOne
    @JoinColumn(name = "place_id")
    var place: Place? = null,

    var scheduledDate: LocalDateTime? = null,

    @Enumerated(EnumType.STRING)
    var status: InstallationOrderStatus = InstallationOrderStatus.SOLICITADO,

    @OneToOne(mappedBy = "installationOrder")
    var subscription: Subscription? = null,

    // Campos de auditoría
    @Column(name = "created_at", nullable = true)
    var createdAt: LocalDateTime? = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = true)
    var updatedAt: LocalDateTime? = null
)
enum class InstallationOrderStatus {
    SOLICITADO,
    EN_CURSO,
    CERRADO,
    CANCELADO
}
