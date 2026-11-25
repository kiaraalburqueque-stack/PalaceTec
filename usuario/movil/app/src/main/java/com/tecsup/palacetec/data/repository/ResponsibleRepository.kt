package com.tecsup.palacetec.data.repository

import com.tecsup.palacetec.data.remote.PalaceTecApi
import com.tecsup.palacetec.data.remote.PauseRequest
import com.tecsup.palacetec.data.remote.PauseResponse
import com.tecsup.palacetec.data.remote.RiskAlerts
import com.tecsup.palacetec.data.remote.SelfExclusionRequest
import com.tecsup.palacetec.data.remote.SelfExclusionResponse
import com.tecsup.palacetec.data.remote.SessionSummary
import com.tecsup.palacetec.data.remote.apiCall

class ResponsibleRepository(private val api: PalaceTecApi) {
    suspend fun sessionSummary(): SessionSummary = apiCall { api.sessionSummary() }

    suspend fun riskAlerts(): RiskAlerts = apiCall { api.riskAlerts() }

    suspend fun pause(incidentId: Long): PauseResponse = apiCall { api.pause(PauseRequest(incidentId)) }

    suspend fun selfExclude(days: Int): SelfExclusionResponse =
        apiCall { api.selfExclude(SelfExclusionRequest(days)) }
}
