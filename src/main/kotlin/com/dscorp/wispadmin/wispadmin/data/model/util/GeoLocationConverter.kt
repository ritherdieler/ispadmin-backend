package com.dscorp.wispadmin.wispadmin.data.model.util

import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import javax.persistence.AttributeConverter


class GeoLocationConverter : AttributeConverter<GeoLocation, String> {

    companion object {
        val mapper = ObjectMapper()
        val reader = mapper.reader().forType(object: TypeReference<GeoLocation> () {})
    }

    override fun convertToDatabaseColumn(geoLocation:GeoLocation): String? {
        var customerInfoJson: String? = null
        try {
            customerInfoJson = mapper.writeValueAsString(geoLocation)
        } catch (e: JsonProcessingException) {
            e.printStackTrace()
        }
        return customerInfoJson
    }


    override fun convertToEntityAttribute(dbData: String?): GeoLocation? {
        var info: GeoLocation? = null
        try {
            info = reader.readValue(dbData)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return info
    }

}