package com.dscorp.wispadmin.acs.controller

import com.dscorp.wispadmin.acs.OnboardingV2ContactRequest
import com.dscorp.wispadmin.acs.OnboardingV2ContactResponse
import com.dscorp.wispadmin.acs.OnboardingV2InternetRequest
import com.dscorp.wispadmin.acs.OnboardingV2InternetCompensateRequest
import com.dscorp.wispadmin.acs.OnboardingV2InternetStatusRequest
import com.dscorp.wispadmin.acs.OnboardingV2InternetStatusResponse
import com.dscorp.wispadmin.acs.OnboardingV2WifiRequest
import com.dscorp.wispadmin.acs.OnboardingV2WifiCompensateRequest
import com.dscorp.wispadmin.acs.OnboardingV2TaskResponse
import com.dscorp.wispadmin.acs.service.OnboardingV2AcsContactService
import com.dscorp.wispadmin.acs.service.OnboardingV2TaskService
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/acs/v1/onboarding-v2")
class OnboardingV2AcsController(
    private val contact: OnboardingV2AcsContactService,
) {
    @PostMapping("/contact")
    fun contact(@RequestBody request: OnboardingV2ContactRequest): OnboardingV2ContactResponse = contact.contact(request)
}

@RestController
@RequestMapping("/api/acs/v1/onboarding-v2")
@ConditionalOnProperty(prefix = "gigafiber.subsystems.acs", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("'\${acs.datasource.url:}'.trim().length() > 0")
class OnboardingV2TaskController(
    private val tasks: OnboardingV2TaskService,
) {
    @PostMapping("/internet")
    fun internet(@RequestBody request: OnboardingV2InternetRequest): OnboardingV2TaskResponse = tasks.enqueueInternet(request)

    @PostMapping("/internet/compensate")
    fun compensateInternet(@RequestBody request: OnboardingV2InternetCompensateRequest): OnboardingV2TaskResponse =
        tasks.compensateInternet(request)

    @PostMapping("/internet/status")
    fun internetStatus(@RequestBody request: OnboardingV2InternetStatusRequest): OnboardingV2InternetStatusResponse =
        tasks.internetStatus(request, compensation = false)

    @PostMapping("/internet/compensate/status")
    fun compensateInternetStatus(@RequestBody request: OnboardingV2InternetStatusRequest): OnboardingV2InternetStatusResponse =
        tasks.internetStatus(request, compensation = true)

    @PostMapping("/wifi")
    fun wifi(@RequestBody request: OnboardingV2WifiRequest): OnboardingV2TaskResponse = tasks.enqueueWifi(request)

    @PostMapping("/wifi/compensate")
    fun compensateWifi(@RequestBody request: OnboardingV2WifiCompensateRequest): OnboardingV2TaskResponse = tasks.compensateWifi(request)

    @PostMapping("/wifi/status")
    fun wifiStatus(@RequestBody request: OnboardingV2WifiCompensateRequest): OnboardingV2InternetStatusResponse = tasks.wifiStatus(request, false)

    @PostMapping("/wifi/compensate/status")
    fun compensateWifiStatus(@RequestBody request: OnboardingV2WifiCompensateRequest): OnboardingV2InternetStatusResponse = tasks.wifiStatus(request, true)
}
