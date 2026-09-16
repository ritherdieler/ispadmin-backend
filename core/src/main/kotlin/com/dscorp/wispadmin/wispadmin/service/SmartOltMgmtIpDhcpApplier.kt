package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets

class SmartOltMgmtIpDhcpApplier(
    private val policy: SmartOltMgmtVlanPolicy,
    private val poster: (String, MultiValueMap<String, Any>) -> Unit,
) {
    fun afterAuthorize(request: OnuAuthorizationRequest) {
        val mgmtVlan = policy.mgmtVlanForCustomerVlan(request.vlan) ?: return
        val body = LinkedMultiValueMap<String, Any>()
        body.add("vlan", mgmtVlan)
        val externalId = UriUtils.encodePathSegment(request.sn.trim(), StandardCharsets.UTF_8)
        poster("onu/set_onu_mgmt_ip_dhcp/$externalId", body)
    }
}
