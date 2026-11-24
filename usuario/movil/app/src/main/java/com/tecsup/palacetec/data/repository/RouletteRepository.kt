package com.tecsup.palacetec.data.repository

import com.tecsup.palacetec.data.remote.BetRequest
import com.tecsup.palacetec.data.remote.BetResponse
import com.tecsup.palacetec.data.remote.PalaceTecApi
import com.tecsup.palacetec.data.remote.RoomState
import com.tecsup.palacetec.data.remote.RouletteRoom
import com.tecsup.palacetec.data.remote.apiCall

class RouletteRepository(private val api: PalaceTecApi) {
    suspend fun rooms(): List<RouletteRoom> = apiCall { api.rooms() }

    suspend fun state(roomId: Long): RoomState = apiCall { api.roomState(roomId) }

    suspend fun placeBet(roomId: Long, request: BetRequest): BetResponse = apiCall { api.placeBet(roomId, request) }
}
