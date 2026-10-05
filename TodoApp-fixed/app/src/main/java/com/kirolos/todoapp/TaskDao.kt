package com.kirolos.todoapp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isDone ASC, createdAt DESC")
    fun getAll(): Flow<List<Task>>

    // للويدجت والتذكيرات (بتتنادى من thread خلفي)
    @Query("SELECT * FROM tasks WHERE isDone = 0 ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate ASC, priority DESC, createdAt DESC")
    fun pendingNow(): List<Task>

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 0")
    fun pendingCountNow(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 0 AND dueDate IS NOT NULL AND dueDate < :today")
    fun overdueCountNow(today: Long): Int

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun byId(id: Int): Task?

    @Query("SELECT * FROM tasks WHERE isDone = 0 AND dueDate IS NOT NULL AND reminderMin IS NOT NULL")
    fun withReminders(): List<Task>

    @Query("SELECT * FROM tasks WHERE cloudId = :cid LIMIT 1")
    fun byCloudId(cid: String): Task?

    @Query("SELECT * FROM tasks")
    fun allNow(): List<Task>

    @Query("SELECT * FROM tasks WHERE isDone = 1")
    fun doneNow(): List<Task>

    @Query("DELETE FROM tasks")
    suspend fun wipe()

    @Query("DELETE FROM tasks WHERE isDone = 1")
    suspend fun clearDone()

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)
}
