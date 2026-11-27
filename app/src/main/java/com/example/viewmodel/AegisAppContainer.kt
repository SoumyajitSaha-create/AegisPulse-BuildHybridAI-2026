package com.example.viewmodel

import android.content.Context
import com.example.ai.HybridAIRouter
import com.example.core.ConnectivityObserver
import com.example.core.TtsSpeaker
import com.example.data.IncidentRepository
import com.example.data.LocalVaultStore

class AegisAppContainer(context: Context) {
    private val appContext = context.applicationContext

    val connectivityObserver = ConnectivityObserver(appContext)
    val localVaultStore = LocalVaultStore(appContext)
    val incidentRepository = IncidentRepository(appContext)
    val hybridAIRouter = HybridAIRouter(
        connectivityObserver = connectivityObserver,
        localVaultStore = localVaultStore,
        incidentRepository = incidentRepository
    )
    val ttsSpeaker = TtsSpeaker(appContext)

    fun dispose() {
        connectivityObserver.unregister()
        ttsSpeaker.shutdown()
    }
}
