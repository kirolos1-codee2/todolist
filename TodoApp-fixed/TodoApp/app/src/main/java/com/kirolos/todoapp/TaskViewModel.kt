package com.kirolos.todoapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val dao = TaskDatabase.getInstance(application).taskDao()

    val tasks: StateFlow<List<Task>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(t: Task) { viewModelScope.launch { TaskActions.save(app, t) } }
    fun toggle(t: Task) { viewModelScope.launch { TaskActions.toggle(app, t) } }
    fun delete(t: Task) { viewModelScope.launch { TaskActions.delete(app, t) } }
    fun restore(t: Task) { viewModelScope.launch { TaskActions.restore(app, t) } }
    fun clearDone() { viewModelScope.launch { TaskActions.clearDone(app) } }
}
