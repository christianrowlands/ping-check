package com.caskfive.pingcheck.repository

import com.caskfive.pingcheck.data.db.HistoryDao
import com.caskfive.pingcheck.data.db.HistoryViewItem
import com.caskfive.pingcheck.data.db.PingResultEntity
import com.caskfive.pingcheck.data.db.PingSessionEntity
import com.caskfive.pingcheck.data.db.TracerouteHopEntity
import com.caskfive.pingcheck.data.db.TracerouteSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao,
    private val pingRepository: PingRepository,
    private val tracerouteRepository: TracerouteRepository,
) {
    fun observeAll(): Flow<List<HistoryViewItem>> {
        return historyDao.observeAll()
    }

    fun search(query: String): Flow<List<HistoryViewItem>> {
        return historyDao.search(query)
    }

    fun observeByType(type: String): Flow<List<HistoryViewItem>> {
        return historyDao.observeByType(type)
    }

    suspend fun deleteSession(id: Long, type: String) {
        when (type) {
            "ping" -> pingRepository.deleteSession(id)
            "traceroute" -> tracerouteRepository.deleteSession(id)
        }
    }

    suspend fun deleteSessionsBefore(beforeTimestamp: Long) {
        pingRepository.deleteSessionsBefore(beforeTimestamp)
        tracerouteRepository.deleteSessionsBefore(beforeTimestamp)
    }

    /**
     * Removes sessions older than 30 days. Intended to be called at app startup.
     */
    suspend fun cleanupOldSessions() {
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        val cutoff = System.currentTimeMillis() - thirtyDaysMs
        deleteSessionsBefore(cutoff)
    }

    suspend fun getPingSession(id: Long): PingSessionEntity? {
        return pingRepository.getSession(id)
    }

    suspend fun getPingResults(sessionId: Long): List<PingResultEntity> {
        return pingRepository.getResults(sessionId)
    }

    suspend fun getTracerouteSession(id: Long): TracerouteSessionEntity? {
        return tracerouteRepository.getSession(id)
    }

    suspend fun getTracerouteHops(sessionId: Long): List<TracerouteHopEntity> {
        return tracerouteRepository.getHops(sessionId)
    }

    suspend fun getRecentRttValues(sessionId: Long, limit: Int = 4): List<Float?> {
        return pingRepository.getRecentRttValues(sessionId, limit)
    }

    suspend fun getHopTimeoutFlags(sessionId: Long, limit: Int = 6): List<Boolean> {
        return tracerouteRepository.getHopTimeoutFlags(sessionId, limit)
    }
}
