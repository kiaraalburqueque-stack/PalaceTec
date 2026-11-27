package com.tecsup.palacetec.data.remote

import com.google.gson.annotations.SerializedName

/** Lo jugado desde que se inició esta sesión. */
data class SessionSummary(
    @SerializedName("total_wagered") val wagered: String,
    @SerializedName("total_paid") val paid: String,
    val net: String,
    @SerializedName("session_started_at") val startedAt: String,
    @SerializedName("duration_seconds") val durationSeconds: Long,
    @SerializedName("pending_wagered") val pendingWagered: String,
    @SerializedName("pending_count") val pendingCount: Long,
    @SerializedName("reality_check_interval_seconds") val realityCheckSeconds: Int,
    val scope: String,
)

data class RiskAlerts(
    /** Fin de la última pausa, que puede haber pasado ya. */
    @SerializedName("pause_until") val pauseUntil: String?,
    val results: List<RiskAlert>,
)

data class RiskAlert(
    @SerializedName("incident_id") val incidentId: Long,
    @SerializedName("created_at") val createdAt: String,
    /** null mientras no se aceptó su pausa; cada alerta da una sola. */
    @SerializedName("pause_started_at") val pauseStartedAt: String?,
    @SerializedName("suggested_pause_seconds") val suggestedPauseSeconds: Long,
)

data class PauseRequest(@SerializedName("incident_id") val incidentId: Long)

data class PauseResponse(@SerializedName("pause_until") val pauseUntil: String?)

data class SelfExclusionRequest(val days: Int)

data class SelfExclusionResponse(
    @SerializedName("self_excluded_until") val until: String,
    val detail: String,
)
