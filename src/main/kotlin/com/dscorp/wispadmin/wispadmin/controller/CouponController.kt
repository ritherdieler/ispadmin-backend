package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Coupon
import com.dscorp.wispadmin.wispadmin.repository.CouponRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/cupon")
class CouponController {

    val objectErrorResponse: ResponseEntity<Coupon> = ResponseEntity.status(500).body(null)
    val listObjectErrorResponse: ResponseEntity<List<Coupon>> = ResponseEntity.status(500).body(null)

    @Autowired
    lateinit var repository: CouponRepository

    @PostMapping
    fun registerCoupon(@RequestBody newCoupon: Coupon): ResponseEntity<Coupon> {
        return try {
            val Coupon = repository.save(newCoupon)
            if (Coupon != null) ResponseEntity.status(200).body(Coupon)
            else objectErrorResponse
        } catch (e: Exception) {
            e.printStackTrace()
            objectErrorResponse
        }
    }


    @GetMapping
    fun getCouponList(): ResponseEntity<List<Coupon>> {
        return try {
            val CouponList = repository.findAll()
            if (CouponList != null) ResponseEntity.status(200).body(CouponList)
            else listObjectErrorResponse
        } catch (e: Exception) {
            e.printStackTrace()
            listObjectErrorResponse
        }
    }

}
