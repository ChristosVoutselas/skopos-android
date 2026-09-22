package com.voutselas.skopos

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject

class GlobeTest {
    private fun record(type: String, lon: Double? = null, lat: Double? = null) = Record(Kind.IOC,
        JSONObject().put("ioc_type",type).apply {
            if(lon != null) put("longitude",lon)
            if(lat != null) put("latitude",lat)
        })
    @Test fun lateMinorityCategoriesSurviveSelectionLimit() {
        val ips = (0..120).map { record("ip:port", it.toDouble(), 20.0) }
        val selected = globeNodes(ips + record("url",10.0,30.0), 20)
        assertEquals(20,selected.size)
        assertTrue(selected.any { it.category == "URLs" })
    }
    @Test fun sharedLocationPreservesDifferentCategoriesButDeduplicatesSameCategory() {
        val ip=record("ip:port",10.0,20.0)
        assertEquals(2,globeNodes(listOf(ip,ip,record("url",10.0,20.0))).size)
    }
    @Test fun unknownGeographyIsNeverInvented() {
        assertTrue(globeNodes(listOf(record("domain"),record("sha256_hash"),record("url",10.0))).isEmpty())
    }
    @Test fun cameraProjectionHasCorrectFrontAndBackHemispheres() {
        assertEquals(1.0,project(Geo(15.0,18.0),15.0,18.0).depth,1e-6)
        assertEquals(-1.0,project(Geo(-165.0,-18.0),15.0,18.0).depth,1e-6)
    }
}
