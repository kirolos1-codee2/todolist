package com.kirolos.todoapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = TaskDatabase.getInstance(application).taskDao()

    val tasks: StateFlow<List<Task>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { dao.insert(Task(title = title.trim())) }
    }

    fun toggleDone(task: Task) {
        viewModelScope.launch { dao.update(task.copy(isDone = !task.isDone)) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { dao.delete(task) }
    }
}
