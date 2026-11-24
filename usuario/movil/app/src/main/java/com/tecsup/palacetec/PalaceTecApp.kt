package com.tecsup.palacetec

import android.app.Application
import com.tecsup.palacetec.data.local.HistoryDatabase
import com.tecsup.palacetec.data.local.SessionStore
import com.tecsup.palacetec.data.remote.ApiFactory
import com.tecsup.palacetec.data.repository.AuthRepository
import com.tecsup.palacetec.data.repository.CatalogRepository
import com.tecsup.palacetec.data.repository.DailyLimitRepository
import com.tecsup.palacetec.data.repository.HistoryRepository
import com.tecsup.palacetec.data.repository.RouletteRepository
import com.tecsup.palacetec.data.repository.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PalaceTecApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Inyección manual: una sola instancia de cada dependencia. */
class AppContainer(app: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionStore = SessionStore(app, appScope)
    private val api = ApiFactory.create(
        baseUrl = BuildConfig.BASE_URL,
        token = { sessionStore.token },
        onRejected = { token, message -> appScope.launch { sessionStore.clearIfCurrent(token, message) } },
    )
    private val historyDao = HistoryDatabase.create(app).historyDao()

    val authRepository = AuthRepository(api, sessionStore, historyDao)
    val walletRepository = WalletRepository(api)
    val catalogRepository = CatalogRepository(api)
    val dailyLimitRepository = DailyLimitRepository(api)
    val rouletteRepository = RouletteRepository(api)
    val historyRepository = HistoryRepository(api, historyDao, owner = { sessionStore.email })
}
