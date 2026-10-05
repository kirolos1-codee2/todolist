package com.kirolos.todoapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val priority: Int = 0,      // 0 بدون، 1 منخفضة، 2 متوسطة، 3 عالية
    val dueDate: Long? = null   // بداية اليوم بالـ millis
)
