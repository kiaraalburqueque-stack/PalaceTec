package com.tecsup.palacetec.data.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import java.math.BigDecimal

/** Eventos de uso. Nunca llevan datos personales (correo, DNI ni token). */
interface AnalyticsTracker {
    fun login()
    fun signUp()
    fun placeBet(amount: BigDecimal, game: String)
    fun viewHistory()
}

class FirebaseAnalyticsTracker(private val analytics: FirebaseAnalytics) : AnalyticsTracker {

    override fun login() = analytics.logEvent(
        FirebaseAnalytics.Event.LOGIN, Bundle().apply { putString(FirebaseAnalytics.Param.METHOD, METHOD) },
    )

    override fun signUp() = analytics.logEvent(
        FirebaseAnalytics.Event.SIGN_UP, Bundle().apply { putString(FirebaseAnalytics.Param.METHOD, METHOD) },
    )

    override fun placeBet(amount: BigDecimal, game: String) = analytics.logEvent(
        "place_bet",
        Bundle().apply {
            putDouble("monto", amount.toDouble())
            putString("juego", game)
        },
    )

    override fun viewHistory() = analytics.logEvent("view_history", null)

    private companion object {
        const val METHOD = "correo"
    }
}

/** Sin google-services.json no hay Firebase y los eventos se descartan. */
object NoAnalytics : AnalyticsTracker {
    override fun login() = Unit
    override fun signUp() = Unit
    override fun placeBet(amount: BigDecimal, game: String) = Unit
    override fun viewHistory() = Unit
}
