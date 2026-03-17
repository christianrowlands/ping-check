package com.caskfive.pingcheck.repository

import com.caskfive.pingcheck.data.db.PingDao
import com.caskfive.pingcheck.data.db.PingResultEntity
import com.caskfive.pingcheck.data.db.PingSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PingRepository @Inject constructor(
    private val pingDao: PingDao,
) {
    suspend fun createSession(session: PingSessionEntity): Long {
        return pingDao.insertSession(session)
    }

    suspend fun updateSession(session: PingSessionEntity) {
        pingDao.updateSession(session)
    }

    suspend fun getSession(id: Long): PingSessionEntity? {
        return pingDao.getSession(id)
    }

    fun observeSession(id: Long): Flow<PingSessionEntity?> {
        return pingDao.observeSession(id)
    }

    suspend fun insertResult(result: PingResultEntity) {
        pingDao.insertResult(result)
    }

    suspend fun insertResults(results: List<PingResultEntity>) {
        pingDao.insertResults(results)
    }

    fun observeResults(sessionId: Long): Flow<List<PingResultEntity>> {
        return pingDao.observeResults(sessionId)
    }

    suspend fun getResults(sessionId: Long): List<PingResultEntity> {
        return pingDao.getResults(sessionId)
    }

    suspend fun getResultCount(sessionId: Long): Int {
        return pingDao.getResultCount(sessionId)
    }

    suspend fun deleteOldestResults(sessionId: Long, count: Int) {
        pingDao.deleteOldestResults(sessionId, count)
    }

    fun observeAllSessions(): Flow<List<PingSessionEntity>> {
        return pingDao.observeAllSessions()
    }

    suspend fun deleteSession(id: Long) {
        pingDao.deleteSession(id)
    }

    suspend fun deleteSessionsBefore(beforeTimestamp: Long) {
        pingDao.deleteSessionsBefore(beforeTimestamp)
    }

    suspend fun getRecentRttValues(sessionId: Long, limit: Int = 4): List<Float?> {
        return pingDao.getRecentRttValues(sessionId, limit)
    }
}
