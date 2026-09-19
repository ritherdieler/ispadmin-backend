package com.dscorp.wispadmin.oltgateway.parser

import org.springframework.stereotype.Component

@Component
class OntLineProfileGemParser {

    private val gemIndexRegex = Regex("""<Gem Index\s+(\d+)>""")
    private val mappingRowRegex = Regex("""^\s*(\d+)\s+(\d+)\s+""")

    fun parse(output: String): List<ParsedGemVlanMapping> {
        val mappings = mutableListOf<ParsedGemVlanMapping>()
        var gem: Int? = null
        for (line in output.lineSequence()) {
            val gemMatch = gemIndexRegex.find(line)
            if (gemMatch != null) {
                gem = gemMatch.groupValues[1].toInt()
                continue
            }
            val currentGem = gem ?: continue
            val row = mappingRowRegex.find(line) ?: continue
            val mapIndex = row.groupValues[1].toInt()
            val vlan = row.groupValues[2].toInt()
            if (vlan in 1..4094) {
                mappings += ParsedGemVlanMapping(gem = currentGem, mapIndex = mapIndex, vlan = vlan)
            }
        }
        return mappings
    }
}
