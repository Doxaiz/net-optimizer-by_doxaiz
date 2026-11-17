package com.doxaiz.netoptimizer.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

/** Сохранённая сессия: графики пинга/скорости/сигнала + метаданные */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val timestamp: Long,
    val mode: String,               // Обычный / Игра / USB-модем
    val durationSec: Long,
    val avgPingMs: Int,
    val maxPingMs: Int,
    val lossPercent: Int,
    val avgSpeedMbps: Double,
    val dropsCount: Int,            // число провалов в 3G/E
    val avgRsrp: Int,
    val band: String,
    val dnsName: String,
    val operator: String,
    val pingHistory: String,        // JSON-массив чисел
    val pinned: Boolean = false
)

class Converters {
    @TypeConverter fun fromList(value: List<Int>): String = value.joinToString(",")
    @TypeConverter fun toList(value: String): List<Int> =
        if (value.isBlank()) emptyList() else value.split(",").mapNotNull { it.toIntOrNull() }
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY pinned DESC, timestamp DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: SessionEntity): Long

    @Query("UPDATE sessions SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?
}

@Database(entities = [SessionEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class DoxaizDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}

class SessionRepository(private val dao: SessionDao) {
    fun observeAll(): Flow<List<SessionEntity>> = dao.observeAll()
    suspend fun find(id: Long): SessionEntity? = dao.get(id)
    suspend fun save(session: SessionEntity): Long {
        val inserted = dao.insert(session)
        if (inserted != -1L) return inserted
        val existing = dao.get(session.id)
        if (existing != null && existing.timestamp == session.timestamp && existing.mode == session.mode) return existing.id
        throw IllegalStateException("Session ID already belongs to another session")
    }
    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)
    suspend fun delete(id: Long) = dao.delete(id)
}
