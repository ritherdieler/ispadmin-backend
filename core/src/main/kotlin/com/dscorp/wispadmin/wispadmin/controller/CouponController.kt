package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.CouponRequestDto
import com.dscorp.wispadmin.wispadmin.dto.CouponResponseDto
import com.dscorp.wispadmin.wispadmin.service.CouponService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid

@RestController
@RequestMapping("/cupon")
class CouponController(
    private val couponService: CouponService
) {

    @PostMapping
    fun registerCoupon(@Valid @RequestBody request: CouponRequestDto): ResponseEntity<CouponResponseDto> =
        ResponseEntity.ok(couponService.register(request))

    @GetMapping
    fun getCouponList(): ResponseEntity<List<CouponResponseDto>> =
        ResponseEntity.ok(couponService.findAll())
}
