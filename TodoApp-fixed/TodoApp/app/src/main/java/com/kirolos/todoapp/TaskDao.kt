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

    // للويدجت (بتتنادى من thread خلفي)
    @Query("SELECT * FROM tasks WHERE isDone = 0 ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate ASC, priority DESC, createdAt DESC")
    fun pendingNow(): List<Task>

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 0")
    fun pendingCountNow(): Int

    @Query("UPDATE tasks SET isDone = NOT isDone WHERE id = :id")
    suspend fun toggleById(id: Int)

    @Query("DELETE FROM tasks WHERE isDone = 1")
    suspend fun clearDone()

    @Insert
    suspend fun insert(task: Task)

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)
}
