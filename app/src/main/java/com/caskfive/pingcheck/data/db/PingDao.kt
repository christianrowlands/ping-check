package com.caskfive.pingcheck.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PingDao {
    @Insert
    suspend fun insertSession(session: PingSessionEntity): Long

    @Update
    suspend fun updateSession(session: PingSessionEntity)

    @Insert
    suspend fun insertResult(result: PingResultEntity)

    @Insert
    suspend fun insertResults(results: List<PingResultEntity>)

    @Query("SELECT * FROM ping_sessions WHERE id = :id")
    suspend fun getSession(id: Long): PingSessionEntity?

    @Query("SELECT * FROM ping_sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<PingSessionEntity?>

    @Query("SELECT * FROM ping_results WHERE session_id = :sessionId ORDER BY sequence_number ASC")
    fun observeResults(sessionId: Long): Flow<List<PingResultEntity>>

    @Query("SELECT * FROM ping_results WHERE session_id = :sessionId ORDER BY sequence_number ASC")
    suspend fun getResults(sessionId: Long): List<PingResultEntity>

    @Query("SELECT COUNT(*) FROM ping_results WHERE session_id = :sessionId")
    suspend fun getResultCount(sessionId: Long): Int

    @Query("""
        DELETE FROM ping_results WHERE session_id = :sessionId
        AND id IN (
            SELECT id FROM ping_results WHERE session_id = :sessionId
            ORDER BY sequence_number ASC LIMIT :count
        )
    """)
    suspend fun deleteOldestResults(sessionId: Long, count: Int)

    @Query("SELECT * FROM ping_sessions ORDER BY start_time DESC")
    fun observeAllSessions(): Flow<List<PingSessionEntity>>

    @Query("DELETE FROM ping_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM ping_sessions WHERE start_time < :beforeTimestamp")
    suspend fun deleteSessionsBefore(beforeTimestamp: Long)

    @Query("SELECT rtt_ms FROM ping_results WHERE session_id = :sessionId AND is_success = 1 ORDER BY sequence_number DESC LIMIT :limit")
    suspend fun getRecentRttValues(sessionId: Long, limit: Int = 4): List<Float?>
}
