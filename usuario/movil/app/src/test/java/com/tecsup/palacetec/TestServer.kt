package com.tecsup.palacetec

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.tecsup.palacetec.data.analytics.AnalyticsTracker
import com.tecsup.palacetec.data.local.HistoryDao
import com.tecsup.palacetec.data.local.HistoryEntity
import com.tecsup.palacetec.data.remote.ApiFactory
import com.tecsup.palacetec.data.remote.PalaceTecApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Respuesta real de Spring guardada en src/test/resources/api. */
fun fixture(name: String): String =
    checkNotNull(TestServer::class.java.getResource("/api/$name.json")) { "Falta $name.json" }.readText()

/** Servidor HTTP local que responde con las respuestas reales grabadas. */
class TestServer {
    private val server = MockWebServer()
    private val responses = ConcurrentHashMap<String, () -> MockResponse>()
    val requests = CopyOnWriteArrayList<Pair<String, String>>()
    private val queries = CopyOnWriteArrayList<Pair<String, String>>()
    val rejected = CopyOnWriteArrayList<Pair<String, String?>>()
    @Volatile var token: String? = "token-de-prueba"
    @Volatile var lastAuthorization: String? = null

    val api: PalaceTecApi

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val key = "${request.method} ${request.path.orEmpty().substringBefore('?').removePrefix("/api/v1")}"
                requests += key to request.body.readUtf8()
                queries += key to request.path.orEmpty().substringAfter('?', "")
                lastAuthorization = request.getHeader("Authorization")
                return responses[key]?.invoke() ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        api = ApiFactory.create(
            server.url("/").toString(), { token }, { token, message -> rejected += token to message },
        )
    }

    fun reply(key: String, fixture: String, status: Int = 200) {
        responses[key] = { MockResponse().setResponseCode(status).setBody(fixture(fixture)) }
    }

    fun replyBody(key: String, body: String, status: Int = 200) {
        responses[key] = { MockResponse().setResponseCode(status).setBody(body) }
    }

    fun disconnect(key: String) {
        responses[key] = { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) }
    }

    fun count(key: String) = requests.count { it.first == key }

    fun bodies(key: String) = requests.filter { it.first == key }.map { it.second }

    fun queries(key: String) = queries.filter { it.first == key }.map { it.second }

    fun close() = server.shutdown()
}

fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T =
    runBlocking { withTimeout(8_000) { first(predicate) } }

class FakeHistoryDao : HistoryDao {
    val rows = CopyOnWriteArrayList<HistoryEntity>()

    override suspend fun find(owner: String) = rows.filter { it.owner == owner }.sortedBy { it.position }

    override suspend fun insert(items: List<HistoryEntity>) {
        rows += items
    }

    override suspend fun delete(owner: String) {
        rows.removeAll { it.owner == owner }
    }

    override suspend fun deleteAll() = rows.clear()
}

class FakeAnalytics : AnalyticsTracker {
    val events = CopyOnWriteArrayList<String>()

    override fun login() {
        events += "login"
    }

    override fun signUp() {
        events += "sign_up"
    }

    override fun placeBet(amount: BigDecimal, game: String) {
        events += "place_bet $amount $game"
    }

    override fun viewHistory() {
        events += "view_history"
    }
}

class MemoryDataStore : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(data.value).also { data.value = it }
}
