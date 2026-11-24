package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY isCompleted ASC, startTimeMillis ASC")
    fun observeAllSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE isCompleted = 0 ORDER BY startTimeMillis ASC")
    suspend fun getAllIncompleteSchedules(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules ORDER BY isCompleted ASC, startTimeMillis ASC")
    suspend fun getAllSchedules(): List<ScheduleEntity>

    @Query("DELETE FROM schedules")
    suspend fun deleteAllSchedules()

    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun getScheduleById(id: Long): ScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ScheduleEntity): Long

    @Update
    suspend fun updateSchedule(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM schedules WHERE isTestSchedule = 1 AND isCompleted = 0")
    suspend fun getActiveTestSchedules(): List<ScheduleEntity>

    @Query("DELETE FROM schedules WHERE isTestSchedule = 1 AND isCompleted = 1")
    suspend fun deleteCompletedTestSchedules()

    @Query("DELETE FROM schedules WHERE isTestSchedule = 1")
    suspend fun deleteAllTestSchedules()

    @Query(
        "UPDATE schedules SET customTagName = :name, customTagColorHex = :colorHex WHERE customTagId = :tagId",
    )
    suspend fun updateCustomTagMetadata(tagId: String, name: String, colorHex: String)

    @Query(
        "UPDATE schedules SET customTagId = '', customTagName = '', customTagColorHex = '' WHERE customTagId = :tagId",
    )
    suspend fun clearDeletedCustomTag(tagId: String)
}
