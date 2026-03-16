package com.caskfive.pingcheck

import android.app.Application
import com.caskfive.pingcheck.repository.HistoryRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PingCheckApp : Application() {

    @Inject
    lateinit var historyRepository: HistoryRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            historyRepository.cleanupOldSessions()
        }
    }
}
