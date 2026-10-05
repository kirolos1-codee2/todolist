package com.kirolos.todoapp

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val priority: Int = 0,      // 0 بدون، 1 منخفضة، 2 متوسطة، 3 عالية
    val dueDate: Long? = null,  // بداية اليوم بالـ millis
    @ColumnInfo(defaultValue = "''") val notes: String = "",
    @ColumnInfo(defaultValue = "0") val repeatMode: Int = 0,  // 0 بدون، 1 يومي، 2 أسبوعي، 3 شهري
    val reminderMin: Int? = null,  // وقت التذكير: دقائق من بداية اليوم (محتاج dueDate)
    @ColumnInfo(defaultValue = "''") val cloudId: String = ""  // معرّف المهمة على السحابة (بيربط التطبيق بالإكستنشن)
)
