package com.kirolos.todoapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = TaskDatabase.getInstance(application).taskDao()

    val tasks: StateFlow<List<Task>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // أي تغيير بيتحفظ وبعدها الويدجت بيتحدّث
    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            block()
            TodoWidgetProvider.refresh(getApplication<Application>())
        }
    }

    fun save(t: Task) = io { if (t.id == 0) dao.insert(t) else dao.update(t) }
    fun toggle(t: Task) = io { dao.update(t.copy(isDone = !t.isDone)) }
    fun delete(t: Task) = io { dao.delete(t) }
    fun clearDone() = io { dao.clearDone() }
}
