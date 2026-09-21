package com.voutselas.skopos

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

class Store(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("skopos", 0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var base by mutableStateOf(prefs.getString("backend", DEFAULT_BACKEND) ?: DEFAULT_BACKEND); private set
    var snapshot by mutableStateOf<Dataset?>(null); private set
    var apt by mutableStateOf<Dataset?>(null); private set
    var hunt by mutableStateOf<Dataset?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var aptError by mutableStateOf<String?>(null); private set
    var huntError by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var aptLoading by mutableStateOf(false); private set
    var huntLoading by mutableStateOf(false); private set
    var query by mutableStateOf(HuntQuery()); private set
    var terms by mutableStateOf<List<String>>(emptyList()); private set
    var saved by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var storageError by mutableStateOf<String?>(null); private set
    var investigation by mutableStateOf<List<Record>>(emptyList()); private set
    var investigationMessage by mutableStateOf<String?>(null); private set
    var investigating by mutableStateOf(false); private set
    private var localReadable = true
    private var refreshJob: Job? = null
    private var aptJob: Job? = null
    private var huntJob: Job? = null
    private var investigationJob: Job? = null
    private var lastAttempt = 0L
    private var aptAttempt = 0L
    private var huntAttempt = 0L
    private var online: Boolean? = null
    fun connectivity(available: Boolean) {
        val restored = online == false && available
        online = available
        if(!available) {
            snapshot = snapshot?.copy(cached = true, notice = "Offline · Saved intelligence")
            apt = apt?.copy(cached = true, notice = "Offline · Saved intelligence")
            hunt = hunt?.copy(cached = true, notice = "Offline · Saved intelligence")
        }
        if(restored) { lastAttempt = 0; aptAttempt = 0; huntAttempt = 0 }
    }
    init {
        runCatching {
            terms = JSONArray(prefs.getString("terms", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } }
            saved = JSONArray(prefs.getString("saved", "[]")).objects()
        }.onFailure { localReadable = false; storageError = "Saved watchlist could not be read. Existing data has not been overwritten." }
    }
    private fun file(key: String) = AtomicFile(File(getApplication<Application>().cacheDir, "skopos-${cacheKey(key)}.json"))
    private suspend fun cached(key: String): Dataset? = withContext(Dispatchers.IO) {
        runCatching { JSONObject(file(key).openRead().bufferedReader().use { it.readText() }) }.getOrNull()?.let {
            runCatching { Dataset(it.getJSONObject("data"), it.getLong("updated"), true, "Showing saved intelligence. Check its original retrieval time.") }.getOrNull()
        }
    }
    private suspend fun load(key: String, fetch: suspend () -> JSONObject): Dataset {
        try {
            val data = fetch(); currentCoroutineContext().ensureActive()
            val time = System.currentTimeMillis()
            val savedOK = withContext(Dispatchers.IO) {
                val target = file(key)
                runCatching {
                    val stream = target.startWrite()
                    try { stream.write(JSONObject().put("updated", time).put("data", data).toString().toByteArray()); target.finishWrite(stream) }
                    catch (e: Exception) { target.failWrite(stream); throw e }
                }.isSuccess
            }
            return Dataset(data, time, notice = if (savedOK) null else "Updated; offline cache could not be saved.")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { return cached(key)?.copy(notice = "Connection unavailable. Showing saved intelligence. ${message(e)}") ?: throw e }
    }
    fun tick(section: String) {
        val now = System.currentTimeMillis()
        if (now - lastAttempt >= 60000) refresh()
        if (section in listOf("APT Campaigns", "Watchlist Setup") && now - aptAttempt >= 60000) refreshAPT()
        if (section == "IOC Hunt" && now - huntAttempt >= 60000) refreshHunt()
    }
    fun refreshCurrent(section: String) { refresh(); if (section in listOf("APT Campaigns", "Watchlist Setup")) refreshAPT(); if(section == "IOC Hunt") refreshHunt() }
    fun refresh() {
        if (refreshJob?.isActive == true) return
        val backend = base; lastAttempt = System.currentTimeMillis()
        refreshJob = scope.launch {
            loading = true; error = null
            try {
                if(snapshot == null) snapshot = cached("$backend|snapshot")
                val result = load("$backend|snapshot") { Backend(backend).snapshot() }
                if (base == backend) snapshot = result
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { error = message(e); snapshot = snapshot?.copy(cached = true) }
            finally { loading = false }
        }
    }
    fun refreshAPT() {
        if (aptJob?.isActive == true) return
        val backend = base; aptAttempt = System.currentTimeMillis()
        aptJob = scope.launch {
            aptLoading = true; aptError = null
            try {
                if(apt == null) apt = cached("$backend|apt")
                val result = load("$backend|apt") { Backend(backend).campaigns() }
                if(base == backend) apt = result
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { aptError = message(e); apt = apt?.copy(cached = true) }
            finally { aptLoading = false }
        }
    }
    fun submitHunt(next: HuntQuery) {
        huntJob?.cancel(); huntJob = null
        if(query != next) { hunt = null; huntError = null }
        query = next.copy(text = next.text.trim()); refreshHunt()
    }
    fun refreshHunt() {
        if(huntJob?.isActive == true) return
        val backend = base; val q = query; huntAttempt = System.currentTimeMillis()
        huntJob = scope.launch {
            huntLoading = true; huntError = null
            try {
                if(hunt == null) hunt = cached("$backend|hunt|${q.key}")
                val result = load("$backend|hunt|${q.key}") { Backend(backend).hunt(q) }
                if(base == backend && query == q) hunt = result
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { huntError = message(e); hunt = hunt?.copy(cached = true) }
            finally { huntLoading = false }
        }
    }
    fun configure(value: String): String? {
        val url = runCatching { backendURL(value) }.getOrElse { return it.message }
        if(!prefs.edit().putString("backend", url).commit()) return "The connection could not be saved."
        refreshJob?.cancel(); aptJob?.cancel(); huntJob?.cancel(); investigationJob?.cancel()
        refreshJob = null; aptJob = null; huntJob = null
        base = url; snapshot = null; apt = null; hunt = null; error = null; aptError = null; huntError = null
        investigation = emptyList(); investigationMessage = null
        lastAttempt = 0; aptAttempt = 0; huntAttempt = 0
        refresh()
        return null
    }
    fun investigate(input: String) {
        val normalized = runCatching { normalizedIndicator(input) }.getOrElse { investigationMessage = it.message; return }
        investigationJob?.cancel()
        val backend = base
        investigationJob = scope.launch {
            investigating = true; investigationMessage = null; investigation = emptyList()
            try {
                val rows = Backend(backend).hunt(HuntQuery(normalized, 720)).getJSONArray("iocs").objects().map { Record(Kind.IOC, it) }
                if (base == backend) { investigation = rows; investigationMessage = if(rows.isEmpty()) "No matches in the last 30 days. A missing match does not establish safety." else "${rows.size} backend matches from the last 30 days (up to 1,000)." }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { investigationMessage = message(e) }
            finally { investigating = false }
        }
    }
    fun saveTerm(input: String, editing: String?): String? {
        if(!localReadable) return storageError
        val term = input.trim()
        if(term.length < 2) return "Enter at least two characters."
        if(terms.any { it != editing && it.equals(term, true) }) return "This term is already on your watchlist."
        val next = if(editing == null) terms + term else terms.map { if(it == editing) term else it }
        if(!prefs.edit().putString("terms", JSONArray(next).toString()).commit()) return "The term could not be saved."
        terms = next; return null
    }
    fun removeTerm(term: String) {
        if(!localReadable) return
        val next = terms - term
        if(prefs.edit().putString("terms", JSONArray(next).toString()).commit()) terms = next else storageError = "Your change could not be saved."
    }
    fun isSaved(record: Record) = saved.any { it.text("id") == "$base|${record.id}" }
    fun toggleSave(record: Record) {
        if(!localReadable) return
        val id = "$base|${record.id}"
        val next = if(isSaved(record)) saved.filter { it.text("id") != id } else listOf(JSONObject().put("id", id).put("title", record.title).put("kind", record.kind.label).put("detail", record.description)) + saved
        persistSaved(next)
    }
    fun removeSaved(item: JSONObject) { if(localReadable) persistSaved(saved.filter { it.text("id") != item.text("id") }) }
    private fun persistSaved(next: List<JSONObject>) {
        if(prefs.edit().putString("saved", JSONArray(next).toString()).commit()) saved = next else storageError = "Your watchlist change could not be saved."
    }
    override fun onCleared() { scope.cancel() }
}
private fun message(error: Exception): String = when(error) {
    is java.net.UnknownHostException -> "Cannot reach the SKOPOS backend. Check your connection and backend URL."
    is java.net.SocketTimeoutException -> "The backend timed out. Pull to refresh or retry."
    is javax.net.ssl.SSLException -> "A secure connection could not be established. Check the backend certificate."
    else -> error.message ?: "Connection unavailable. Please retry."
}
