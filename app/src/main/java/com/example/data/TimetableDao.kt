package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {

    // --- Timetables ---
    @Query("SELECT * FROM timetables ORDER BY createdAtMillis ASC")
    fun observeAllTimetables(): Flow<List<TimetableEntity>>

    @Query("SELECT * FROM timetables ORDER BY createdAtMillis ASC")
    suspend fun getAllTimetables(): List<TimetableEntity>

    @Query("SELECT * FROM timetables WHERE isActive = 1 LIMIT 1")
    fun observeActiveTimetable(): Flow<TimetableEntity?>

    @Query("SELECT * FROM timetables WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTimetable(): TimetableEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimetable(timetable: TimetableEntity): Long

    @Update
    suspend fun updateTimetable(timetable: TimetableEntity)

    @Query("UPDATE timetables SET isActive = CASE WHEN id = :timetableId THEN 1 ELSE 0 END")
    suspend fun setActiveTimetable(timetableId: Long)

    @Query("DELETE FROM timetables WHERE id = :timetableId")
    suspend fun deleteTimetableById(timetableId: Long)

    @Query("DELETE FROM timetables")
    suspend fun deleteAllTimetables()

    // --- Courses ---
    @Query("SELECT * FROM courses WHERE timetableId = :timetableId ORDER BY dayOfWeek ASC, startSection ASC")
    fun observeCoursesForTimetable(timetableId: Long): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE timetableId = :timetableId ORDER BY dayOfWeek ASC, startSection ASC")
    suspend fun getCoursesForTimetable(timetableId: Long): List<CourseEntity>

    @Query("SELECT * FROM courses ORDER BY timetableId ASC, dayOfWeek ASC, startSection ASC")
    suspend fun getAllCourses(): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :courseId LIMIT 1")
    suspend fun getCourseById(courseId: Long): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Update
    suspend fun updateCourse(course: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourseById(courseId: Long)

    @Query("DELETE FROM courses WHERE timetableId = :timetableId")
    suspend fun deleteCoursesForTimetable(timetableId: Long)

    // --- Section Times (6..16 customizable) ---
    @Query("SELECT * FROM section_times WHERE timetableId = :timetableId ORDER BY sectionNumber ASC")
    fun observeSectionTimes(timetableId: Long): Flow<List<SectionTimeEntity>>

    @Query("SELECT * FROM section_times WHERE timetableId = :timetableId ORDER BY sectionNumber ASC")
    suspend fun getSectionTimes(timetableId: Long): List<SectionTimeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSectionTimes(times: List<SectionTimeEntity>)

    @Query("DELETE FROM section_times WHERE timetableId = :timetableId")
    suspend fun deleteSectionTimesForTimetable(timetableId: Long)

    @Transaction
    suspend fun replaceSectionTimes(timetableId: Long, times: List<SectionTimeEntity>) {
        deleteSectionTimesForTimetable(timetableId)
        upsertSectionTimes(times.map { it.copy(timetableId = timetableId) })
    }

    // --- Per-day Course Occurrence States (Complete / Snooze 10m / Dismiss) ---
    @Query("SELECT * FROM course_occurrence_states WHERE dateKey = :dateKey")
    fun observeCourseStatesForDate(dateKey: String): Flow<List<CourseOccurrenceStateEntity>>

    @Query("SELECT * FROM course_occurrence_states WHERE dateKey = :dateKey")
    suspend fun getCourseStatesForDate(dateKey: String): List<CourseOccurrenceStateEntity>

    @Query("SELECT * FROM course_occurrence_states WHERE courseId = :courseId AND dateKey = :dateKey LIMIT 1")
    suspend fun getCourseState(courseId: Long, dateKey: String): CourseOccurrenceStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourseState(state: CourseOccurrenceStateEntity)

    // --- Study Tasks ---
    @Query("SELECT * FROM study_tasks ORDER BY isCompleted ASC, createdAtMillis DESC")
    fun observeStudyTasks(): Flow<List<StudyTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyTask(task: StudyTaskEntity): Long

    @Update
    suspend fun updateStudyTask(task: StudyTaskEntity)

    @Query("DELETE FROM study_tasks WHERE id = :taskId")
    suspend fun deleteStudyTaskById(taskId: Long)

    @Transaction
    suspend fun replaceTimetableCourses(timetableId: Long, courses: List<CourseEntity>) {
        deleteCoursesForTimetable(timetableId)
        insertCourses(courses.map { it.copy(id = 0L, timetableId = timetableId) })
    }
}
