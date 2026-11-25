package com.tecsup.palacetec

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.tecsup.palacetec.data.analytics.AnalyticsTracker
import com.tecsup.palacetec.data.analytics.FirebaseAnalyticsTracker
import com.tecsup.palacetec.data.analytics.NoAnalytics
import com.tecsup.palacetec.data.local.AndroidAvatarSource
import com.tecsup.palacetec.data.local.HistoryDatabase
import com.tecsup.palacetec.data.local.PushStore
import com.tecsup.palacetec.data.local.SessionStore
import com.tecsup.palacetec.data.local.pushDataStore
import com.tecsup.palacetec.data.local.sessionDataStore
import com.tecsup.palacetec.data.remote.ApiFactory
import com.tecsup.palacetec.data.repository.AndroidPushPlatform
import com.tecsup.palacetec.data.repository.AuthRepository
import com.tecsup.palacetec.data.repository.CatalogRepository
import com.tecsup.palacetec.data.repository.DailyLimitRepository
import com.tecsup.palacetec.data.repository.HistoryRepository
import com.tecsup.palacetec.data.repository.ProfileRepository
import com.tecsup.palacetec.data.repository.PushRepository
import com.tecsup.palacetec.data.repository.ResponsibleRepository
import com.tecsup.palacetec.data.repository.RouletteRepository
import com.tecsup.palacetec.data.repository.WalletRepository
import com.tecsup.palacetec.push.PushNotifications
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
        container.pushNotifications.createChannel()
    }
}

/** Inyección manual: una sola instancia de cada dependencia. */
class AppContainer(app: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionStore = SessionStore(app.sessionDataStore, appScope)
    private val api = ApiFactory.create(
        baseUrl = BuildConfig.BASE_URL,
        token = { sessionStore.token },
        onRejected = { token, message -> appScope.launch { sessionStore.clearIfCurrent(token, message) } },
    )
    private val historyDao = HistoryDatabase.create(app).historyDao()

    /** Falso si se compiló sin google-services.json. */
    private val firebaseReady = FirebaseApp.getApps(app).isNotEmpty()

    val analytics: AnalyticsTracker =
        if (firebaseReady) FirebaseAnalyticsTracker(FirebaseAnalytics.getInstance(app)) else NoAnalytics
    val pushNotifications = PushNotifications(app)
    val pushRepository = PushRepository(AndroidPushPlatform(app, firebaseReady), PushStore(app.pushDataStore))

    val authRepository = AuthRepository(api, sessionStore, historyDao, analytics)
    val walletRepository = WalletRepository(api)
    val catalogRepository = CatalogRepository(api)
    val dailyLimitRepository = DailyLimitRepository(api)
    val responsibleRepository = ResponsibleRepository(api)
    val rouletteRepository = RouletteRepository(api, analytics)
    val profileRepository = ProfileRepository(api, AndroidAvatarSource(app), BuildConfig.BASE_URL)
    val historyRepository = HistoryRepository(api, historyDao, owner = { sessionStore.email })
}
