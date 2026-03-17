package com.caskfive.pingcheck.repository

import com.caskfive.pingcheck.data.db.TracerouteDao
import com.caskfive.pingcheck.data.db.TracerouteHopEntity
import com.caskfive.pingcheck.data.db.TracerouteSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TracerouteRepository @Inject constructor(
    private val tracerouteDao: TracerouteDao,
) {
    suspend fun createSession(session: TracerouteSessionEntity): Long {
        return tracerouteDao.insertSession(session)
    }

    suspend fun updateSession(session: TracerouteSessionEntity) {
        tracerouteDao.updateSession(session)
    }

    suspend fun insertHop(hop: TracerouteHopEntity) {
        tracerouteDao.insertHop(hop)
    }

    suspend fun getSession(id: Long): TracerouteSessionEntity? {
        return tracerouteDao.getSession(id)
    }

    fun observeSession(id: Long): Flow<TracerouteSessionEntity?> {
        return tracerouteDao.observeSession(id)
    }

    fun observeHops(sessionId: Long): Flow<List<TracerouteHopEntity>> {
        return tracerouteDao.observeHops(sessionId)
    }

    suspend fun getHops(sessionId: Long): List<TracerouteHopEntity> {
        return tracerouteDao.getHops(sessionId)
    }

    suspend fun deleteSession(id: Long) {
        tracerouteDao.deleteSession(id)
    }

    fun observeAllSessions(): Flow<List<TracerouteSessionEntity>> {
        return tracerouteDao.observeAllSessions()
    }

    suspend fun deleteSessionsBefore(beforeTimestamp: Long) {
        tracerouteDao.deleteSessionsBefore(beforeTimestamp)
    }

    suspend fun getHopTimeoutFlags(sessionId: Long, limit: Int = 6): List<Boolean> {
        return tracerouteDao.getHopTimeoutFlags(sessionId, limit)
    }
}
