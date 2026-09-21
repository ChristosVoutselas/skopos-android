package com.voutselas.skopos

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.net.InetAddress
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*

const val DEFAULT_BACKEND = "https://api.voutselasgroup.com/skopos/"
fun backendURL(value: String): String {
    val u = runCatching { URI(value.trim()) }.getOrNull()
    require(u != null && u.scheme.equals("https", true) && !u.host.isNullOrBlank() && u.rawUserInfo == null && u.rawQuery == null && u.rawFragment == null) {
        "Enter an HTTPS backend URL without credentials, query or fragment."
    }
    return u.toASCIIString().trimEnd('/') + "/"
}
fun sourceURL(value: String): String? = runCatching {
    val u = URI(value)
    value.takeIf { u.scheme?.lowercase() in listOf("http", "https") && !u.host.isNullOrEmpty() && u.rawUserInfo == null && !u.host.endsWith(".onion", true) }
}.getOrNull()
fun defang(value: String) = value.replace("https://", "hxxps://", true).replace("http://", "hxxp://", true).replace(".", "[.]").replace("@", "[@]")
fun normalizedIndicator(input: String): String {
    val text = input.trim()
    require(text.isNotEmpty() && text.length <= 2048 && text.none { it.isWhitespace() }) { "Enter an IP, domain, HTTP(S) URL or MD5/SHA-1/SHA-256 hash." }
    if (text.matches(Regex("(?i)([a-f0-9]{32}|[a-f0-9]{40}|[a-f0-9]{64})"))) return text.lowercase()
    if (text.matches(Regex("[0-9.]+"))) {
        require(text.split('.').size == 4 && text.split('.').all { it.toIntOrNull() in 0..255 && (it == "0" || !it.startsWith('0')) }) { "Invalid IP address." }
        return text
    }
    // Numeric IPv6 only: this cannot initiate DNS resolution.
    if (text.contains(':') && text.matches(Regex("[a-fA-F0-9:.]+")) && runCatching { InetAddress.getByName(text).address.size == 16 }.getOrDefault(false)) return text.lowercase()
    if (sourceURL(text) != null) return text
    val d = text.removeSuffix(".")
    val parts = d.split('.')
    require(d.length <= 253 && parts.size >= 2 && parts.all { it.length in 1..63 && it.matches(Regex("[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?")) } && parts.last().any { it.isLetter() }) { "Enter an IP, domain, HTTP(S) URL or MD5/SHA-1/SHA-256 hash." }
    return d.lowercase()
}
fun timestamp(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrElse {
    runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrElse { runCatching { LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrDefault(0) }
}
fun JSONObject.text(key: String, fallback: String = ""): String = if (isNull(key)) fallback else optString(key, fallback).ifBlank { fallback }
fun JSONObject.number(key: String): Double? = if (isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun cacheKey(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

enum class Kind(val label: String) { IOC("IOC"), CVE("Vulnerability"), NEWS("News"), CLAIM("Ransomware"), APT("APT campaign") }
data class Record(val kind: Kind, val raw: JSONObject) {
    val id: String get() = "${kind.name}:${raw.text(if (kind == Kind.CVE) "cve" else "id") }"
    fun text(key: String, fallback: String = "Unknown") = raw.text(key, fallback)
    val title: String get() = when(kind) {
        Kind.IOC -> defang(text("value")); Kind.CVE -> text("cve"); Kind.NEWS -> text("title"); Kind.CLAIM -> text("victim"); Kind.APT -> text("name")
    }
    val description: String get() = when(kind) {
        Kind.IOC -> "Confidence is supplied by the source; severity is not supplied. Observation times use effective_seen when upstream timestamps are unavailable."
        Kind.NEWS -> text("summary", "No summary supplied by this source.")
        Kind.CLAIM -> "Reported by ${text("source")}. This is a ransomware claim and does not independently verify a breach."
        else -> text("description", "No description available.").replace(Regex("\\(Citation:[^)]+\\)"), "")
    }
    val severity: String get() = if (kind == Kind.IOC) "Unknown" else text("severity", if (kind == Kind.APT) "Medium" else "Unknown")
    val date: Long get() = timestamp(when(kind) {
        Kind.IOC -> raw.text("last_seen", raw.text("effective_seen")); Kind.CLAIM -> raw.text("discovered")
        Kind.APT -> raw.text("mitre_last_modified", raw.text("last_seen", raw.text("last_updated", raw.text("first_seen"))))
        else -> raw.text("nvd_published", raw.text("published"))
    })
    val link: String? get() = sourceURL(when(kind) {
        Kind.IOC -> raw.text("source_url"); Kind.CVE -> raw.text("nvd_url", "https://nvd.nist.gov/vuln/detail/${raw.text("cve")}")
        Kind.APT -> raw.text("reference_url"); else -> raw.text("url")
    })
    fun matches(query: String): Boolean = query.trim().let { it.isEmpty() || raw.toString().contains(it, true) }
    val category: String get() = when { text("ioc_type").contains("ip", true) -> "IP / C2"; text("ioc_type").contains("domain", true) -> "Domains"; text("ioc_type").contains("url", true) -> "URLs"; else -> "Other" }
    val latitude get() = raw.number("latitude")?.takeIf { it in -90.0..90.0 }
    val longitude get() = raw.number("longitude")?.takeIf { it in -180.0..180.0 }
}
data class Dataset(val json: JSONObject, val updated: Long, val cached: Boolean = false, val notice: String? = null) {
    fun rows(key: String, kind: Kind): List<Record> = json.optJSONArray(key)?.objects()?.map { Record(kind, it) } ?: emptyList()
}
data class HuntQuery(val text: String = "", val hours: Int = 24, val type: String = "") {
    val params get() = mapOf("q" to text.trim(), "hours" to hours.toString(), "ioc_type" to type, "limit" to "1000")
    val key get() = params.toSortedMap().entries.joinToString("&") { "${it.key}=${encode(it.value)}" }
}
fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
class Backend(private val base: String) {
    suspend fun get(path: String, params: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        require(path in listOf("api/summary", "api/iocs", "api/vulnerabilities", "api/news", "api/ransomware", "api/apt/campaigns"))
        val query = params.toSortedMap().entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val connection = URI(backendURL(base) + path + if (query.isEmpty()) "" else "?$query").toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20000; connection.readTimeout = 30000
            connection.setRequestProperty("Accept", "application/json")
            connection.useCaches = false
            val code = connection.responseCode
            check(code in 200..299) { "Backend returned HTTP $code. Check the connection settings. Redirects are not followed." }
            connection.inputStream.bufferedReader().use { it.readText() }.also { ensureActive() }
        } finally { connection.disconnect() }
    }
    suspend fun snapshot(): JSONObject = coroutineScope {
        val endpoints = listOf("summary" to mapOf("hours" to "24"), "iocs" to mapOf("hours" to "24", "limit" to "2000"), "vulnerabilities" to mapOf("limit" to "1000"), "ransomware" to mapOf("limit" to "200"), "news" to mapOf("limit" to "100"))
        val requests = endpoints.map { (path, params) -> async { path to get("api/$path", params) } }
        JSONObject().apply { requests.awaitAll().forEach { (key, value) -> put(key, if(key == "summary") JSONObject(value) else JSONArray(value)) } }
    }
    suspend fun campaigns(): JSONObject {
        val rows = JSONArray(); val ids = mutableSetOf<String>(); var offset = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val page = JSONObject(get("api/apt/campaigns", mapOf("limit" to "200", "offset" to offset.toString())))
            val items = page.getJSONArray("items").objects(); val total = page.getInt("total")
            if (items.isEmpty()) { check(offset >= total) { "Incomplete APT response." }; break }
            val before = ids.size
            items.forEach { if(ids.add(it.get("id").toString())) rows.put(it) }
            check(ids.size > before) { "Backend repeated an APT page." }
            offset += items.size
            if (offset >= total) break
        }
        return JSONObject().put("campaigns", rows)
    }
    suspend fun hunt(query: HuntQuery) = JSONObject().put("iocs", JSONArray(get("api/iocs", query.params)))
}
