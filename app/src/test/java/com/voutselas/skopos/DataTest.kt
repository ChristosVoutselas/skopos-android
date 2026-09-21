package com.voutselas.skopos

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject

class DataTest {
    @Test fun backendPreservesDeploymentPath() { assertEquals("https://api.voutselasgroup.com/skopos/", backendURL(" https://api.voutselasgroup.com/skopos ")) }
    @Test fun backendRejectsUnsafeConfiguration() {
        listOf("http://example.com", "https://user:pass@example.com", "https://example.com?token=x", "https://example.com/#x", "https:///skopos", "file:///tmp/data").forEach { assertTrue(it, runCatching { backendURL(it) }.isFailure) }
    }
    @Test fun indicatorNormalizationDoesNotResolveDomains() {
        assertEquals("example.com", normalizedIndicator("EXAMPLE.COM.")); assertEquals("2001:db8::1", normalizedIndicator("2001:db8::1")); assertEquals("192.0.2.1", normalizedIndicator("192.0.2.1"))
        assertEquals("a".repeat(64), normalizedIndicator("A".repeat(64)))
        listOf("999.1.1.1", "1.2.3", "bad domain", "-bad.example", "https://user:pass@example.com", "javascript:alert(1)").forEach { assertTrue(it, runCatching { normalizedIndicator(it) }.isFailure) }
    }
    @Test fun sourceLinksAreExplicitWebLinks() { assertNull(sourceURL("javascript:alert(1)")); assertNull(sourceURL("https://user@example.com")); assertNull(sourceURL("http://example.onion")); assertEquals("https://example.com/article", sourceURL("https://example.com/article")) }
    @Test fun defangingNeutralizesTargets() { assertEquals("hxxps://example[.]com/a", defang("https://example.com/a")) }
    @Test fun timestampsSupportFractionalOffsets() { assertEquals(timestamp("2026-09-21T10:00:00Z"), timestamp("2026-09-21T12:00:00.000000+02:00")); assertEquals(0, timestamp("bad")) }
    @Test fun absentNumbersRemainUnknown() { val json=JSONObject("{\"critical_recent\":0}"); assertNull(json.number("new_updated_cves_24h")); assertEquals(0.0,json.number("critical_recent")!!,0.0) }
    @Test fun iocConfidenceIsNotSeverity() { val r=Record(Kind.IOC,JSONObject("{\"id\":1,\"value\":\"192.0.2.1\",\"ioc_type\":\"ip:port\",\"confidence\":100,\"effective_seen\":\"2026-09-21T10:00:00Z\"}")); assertEquals("Unknown",r.severity); assertEquals("IP / C2",r.category); assertEquals("192[.]0[.]2[.]1",r.title); assertTrue(r.date>0) }
    @Test fun geographyRejectsInvalidCoordinates() { val r=Record(Kind.IOC,JSONObject("{\"latitude\":91,\"longitude\":-181}")); assertNull(r.latitude); assertNull(r.longitude) }
    @Test fun huntCacheKeysAreQueryAndBackendSpecific() { assertEquals(HuntQuery(" x ").key,HuntQuery("x").key); assertNotEquals(HuntQuery("x").key,HuntQuery("x",6).key); assertNotEquals(cacheKey("https://a.example/|snapshot"),cacheKey("https://b.example/|snapshot")); assertTrue(HuntQuery("a&b").key.contains("a%26b")) }
    @Test fun aptMissingSeverityIsExplicitFallback() { val r=Record(Kind.APT,JSONObject("{\"id\":1,\"name\":\"Example\",\"severity\":null}")); assertEquals("Medium",r.severity); assertTrue(r.raw.isNull("severity")) }
}
