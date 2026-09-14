package com.dscorp.wispadmin.wispadmin.dto

data class FilterRuleDto(
    val id: String,
    val chain: String?,
    val action: String?,
    val srcAddress: String?,
    val dstAddress: String?,
    val protocol: String?,
    val srcPort: String?,
    val dstPort: String?,
    val comment: String?,
    val disabled: Boolean,
    val bytes: Long?,
    val packets: Long?,
    val inInterface: String?,
    val outInterface: String?
) {
    companion object {
        fun fromMikroTikData(data: Map<String, String>): FilterRuleDto {
            return FilterRuleDto(
                id = data[".id"] ?: "",
                chain = data["chain"],
                action = data["action"],
                srcAddress = data["src-address"],
                dstAddress = data["dst-address"],
                protocol = data["protocol"],
                srcPort = data["src-port"],
                dstPort = data["dst-port"],
                comment = data["comment"],
                disabled = data["disabled"]?.equals("true", ignoreCase = true) ?: false,
                bytes = data["bytes"]?.toLongOrNull(),
                packets = data["packets"]?.toLongOrNull(),
                inInterface = data["in-interface"],
                outInterface = data["out-interface"]
            )
        }
    }
}

data class FilterRuleOperationRequest(
    val deviceId: Int,
    val ruleIds: List<String>
)

data class FilterRuleOperationResponse(
    val success: Boolean,
    val message: String,
    val results: Map<String, Boolean>? = null,
    val totalRules: Int = 0,
    val successfulRules: Int = 0,
    val failedRules: Int = 0
) 

data class AddressListEntryDto(
    val id: String,
    val address: String?,
    val listName: String?,
    val comment: String?,
    val disabled: Boolean,
    val timeout: String? = null
) {
    companion object {
        fun fromMikroTikData(data: Map<String, String>): AddressListEntryDto {
            return AddressListEntryDto(
                id = data[".id"] ?: "",
                address = data["address"],
                listName = data["list"],
                comment = data["comment"],
                disabled = data["disabled"]?.equals("true", ignoreCase = true) ?: false,
                timeout = data["timeout"],
            )
        }
    }
}

data class AddressListOperationRequest(
    val deviceId: Int,
    val entryIds: List<String>
)