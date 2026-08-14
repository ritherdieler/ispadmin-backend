package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.controller.toDto
import com.dscorp.wispadmin.wispadmin.data.model.util.GeoLocationConverter
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionCutDto
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionUserDto
import com.dscorp.wispadmin.wispadmin.mapper.toDto
import java.time.LocalDate
import java.util.*
import javax.persistence.*
import javax.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
//    uniqueConstraints = [
//        UniqueConstraint(columnNames = ["napbox_id", "borne_number"], name = "uk_napbox_borne")
//    ],s
    indexes = [
        // Índices para optimizar consultas del dashboard
        Index(name = "idx_subscription_service_status", columnList = "serviceStatus"),
        Index(name = "idx_subscription_subscription_date", columnList = "subscription_date_datetime"),
        Index(name = "idx_subscription_dni", columnList = "dni"),
        Index(name = "idx_subscription_place_subscription_date", columnList = "place_id, subscription_date_datetime"),
        Index(name = "idx_subscription_technician", columnList = "technician_id"),
        Index(name = "idx_subscription_installation_type_date", columnList = "installationType, subscription_date_datetime"),
        Index(name = "idx_subscription_payment_commit_auto_cut", columnList = "isPaymentCommit, auto_cut, serviceStatus")
    ]
)
data class Subscription(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,
    var firstName: String? = null,
    var lastName: String? = null,
    var businessName: String? = null,
    var email: String? = null,
    @Column(unique = true)
    var dni: String? = null,
    var password: String? = null,
    var address: String? = null,
    var phone: String? = null,
    var ruc: String? = null,
    @Enumerated(EnumType.STRING)
    var clientType: ClientType = ClientType.PERSON,

    @OneToOne(fetch = FetchType.EAGER)
    var ipPool: IpPool? = null,

    @Column(unique = true)
    var ip: String? = null,

    @Column(name = "subscription_date_datetime")//edwin
    var subscriptionDatetime: LocalDateTime? = null,
    var isServiceCutOff: Boolean = false,
    @Column(name = "last_cut_off_date")
    var lastCutOffDate: LocalDate? = null,
    @Column(name = "cancellation_date_datetime")//edwin
    var cancellationDateDatetime: LocalDateTime? = null,
    var isNew: Boolean? = false,

    var isPaymentCommit: Boolean? = false,
    @Column(name = "payment_commitment_date_datetime")//edwin
    var paymentCommitmentDateDatetime: LocalDateTime? = null,

    var isReactivation: Boolean? = false,
    @Column(name = "reactivation_date_datetime")//edwin
    var reactivationDateDatetime: LocalDateTime? = null,

    @Convert(converter = GeoLocationConverter::class)
    @Column(columnDefinition = "json")
    var location: GeoLocation? = null,

    @OneToMany(mappedBy = "subscription", orphanRemoval = true)
    var payments: MutableSet<Payment> = mutableSetOf(),

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id")
    var plan: Plan? = null,

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "place_id")
    var place: Place? = null,

    @ManyToMany(targetEntity = NetworkDevice::class, fetch = FetchType.EAGER)
    @JoinTable(
        name = "installed_devices",
        joinColumns = [JoinColumn(name = "subscription_id")],
        inverseJoinColumns = [JoinColumn(name = "network_device_id")]
    )
    var additionalDevices: List<NetworkDevice>? = null,

    @OneToOne(fetch = FetchType.EAGER)
    var hostDevice: NetworkDevice? = null,

    @OneToOne(orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "technician_id")
    var technician: User? = null,

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "napbox_id")
    var napBox: NapBox? = null,

    @OneToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
    var fiberOnu: Onu? = null,

    @OneToOne(fetch = FetchType.EAGER)
    var cpe: NetworkDevice? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installation_order_id", unique = true)
    var installationOrder: InstallationOrder? = null,

    @Enumerated(EnumType.STRING)
    var installationType: InstallationType? = null,

    @Enumerated(EnumType.STRING)
    var serviceStatus: ServiceStatus = ServiceStatus.ACTIVE,

    var price: Double? = null,

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "coupon_id")
    var coupon: Coupon? = null,

    var note: String? = null,
    @OneToMany(mappedBy = "subscription")
    val assistanceTickets: MutableSet<AssistanceTicket> = mutableSetOf(),

    //URL de la foto de fachada guardad en Firebase Storge
    @Column(name = "facade_photo_url", length = 500)
    var facadePhotoUrl: String? = null,

    @OneToMany(mappedBy = "subscription")
    val subscriptionLogs: MutableSet<SubscriptionLog> = mutableSetOf(),

    var isMigration: Boolean? = false,

    var migrationNote: String? = null,

    var migrationPrice: Double? = null,

    var migrationDate: Date? = null,

    @Column(name = "borne_number")
    var borneNumber: String? = null,

    @Column(name = "auto_cut")
    var autoCut: Boolean = true,

    @Column(name = "is_bimonthly", nullable = false)
    var isBimonthly: Boolean? = false,

    @OneToMany(mappedBy = "subscription", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val reconnections: MutableSet<SubscriptionReconnection> = mutableSetOf(),

    @Enumerated(EnumType.STRING)
    var equipmentCondition: EquipmentCondition

) {


    fun toDto() = SubscriptionDto(
        id = id,
        firstName = firstName,
        lastName = lastName,
        dni = dni,
        password = password,
        address = address,
        phone = phone,
        isNew = isNew,
        plan = plan?.toDto(),
        place = place?.toDto(),
        isServiceCutOff = isServiceCutOff,
        additionalDevices = additionalDevices?.toDto(),
        location = location?.toDto(),
        napBox = napBox?.toDto(),
        technician = technician?.toDto(),
        hostDevice = hostDevice?.toDto(),
        installationType = installationType,
        serviceStatus = serviceStatus,
        ip = ip,
        subscriptionDate = subscriptionDatetime
            ?.atZone(java.time.ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli(),
        isMigration = isMigration ?: false,
        price = price,
        paymentCommitmentDate = paymentCommitmentDateDatetime
            ?.atZone(java.time.ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli(),
        isPaymentCommitment = isPaymentCommit,
        lastCutOffDate = lastCutOffDate,
        isReactivation = isReactivation ?: false,
        reactivationDate = reactivationDateDatetime
            ?.atZone(java.time.ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli(),
        note = note,
        email = email,
        facadePhotoUrl = facadePhotoUrl,
        pendingInvoiceQuantity = payments.filter { !it.paid }.size,
        totalDebt = payments.filter { !it.paid }.sumOf { it.amountToPay },
        antiquityInMonths = geSubscriptionAntiquity(),
        qualification = getSubscriptionQualification(),
        ics = 10,
        lastPaymentDate = getLastPaymentDate(),
        borneNumber = borneNumber,
        equipmentCondition = equipmentCondition,
        autoCut = autoCut,
        hasFiberOnu = fiberOnu != null
    )


    fun toCutDto() = SubscriptionCutDto(
        id = id,
        hostDevice = hostDevice?.toDto(),
        ip = ip,
        name = getFullName().uppercase(),
    )

    private fun getLastPaymentDate(): String? {
        val lastPayment = payments
            .filter { it.paid && it.paymentDateDatetime != null }
            .maxByOrNull { it.paymentDateDatetime!! }

        return lastPayment?.paymentDateDatetime?.let {
            "${it.dayOfMonth}/${it.monthValue}/${it.year}"
        }
    }

    fun getSubscriptionQualification(): Int {
        val paidPaymentDays = payments
            .filter { it.paid && it.paymentDateDatetime != null }
            .map { it.paymentDateDatetime!!.dayOfMonth }

        if (paidPaymentDays.isEmpty()) return 0

        val paymentDayAverage = paidPaymentDays.average().toInt()

        return when {
            paymentDayAverage <= 5 -> 5
            paymentDayAverage <= 10 -> 4
            paymentDayAverage <= 15 -> 3
            paymentDayAverage <= 20 -> 2
            paymentDayAverage <= 25 -> 1
            else -> 0
        }
    }

    fun geSubscriptionAntiquity() = payments.groupBy {
        val billingDate = it.billingDateDatetime
        Pair(billingDate.year, billingDate.monthValue)
    }.size



    enum class ClientType {
        PERSON,
        BUSINESS
    }

    override fun toString(): String {
        return "$id $firstName $lastName"
    }

    override fun hashCode(): Int {
        return Objects.hash(
            id,
            firstName,
            lastName,
            businessName,
            email,
            dni,
            address,
            phone,
            ruc,
            clientType ?: ClientType.PERSON,
            ip,
            subscriptionDatetime,
            lastCutOffDate,
            cancellationDateDatetime,
            isNew,
            isPaymentCommit,
            paymentCommitmentDateDatetime,
            isReactivation,
            reactivationDateDatetime,
            location,
            plan,
            place,
            hostDevice,
            technician,
            napBox,
            fiberOnu,
            cpe,
            installationType,
            serviceStatus,
            price,
            coupon,
            note,
            isMigration,
            migrationNote,
            migrationPrice,
            migrationDate,
            autoCut
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Subscription

        return id == other.id
    }

    fun getFullName(): String {
        return firstName.toString() + " " + lastName.toString()
    }


}


data class SubscriptionSearchDto(
    val id: Int,
    val fullName: String,
)

enum class ServiceStatus {
    ACTIVE,
    CUT_OFF,
    SUSPENDED,
    CANCELLED
}


fun Subscription.toSubscriptionUserDto(): SubscriptionUserDto {
    return SubscriptionUserDto(
        id = id,
        firstName = firstName,
        lastName = lastName,
        phone = phone,
        dni = dni,
        address = address,
    )
}


enum class EquipmentCondition {
    LOAN,
    SOLD
}
