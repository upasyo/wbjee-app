package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.Notice
import com.example.data.repository.NoticeRepository
import com.example.data.worker.WorkScheduler
import com.example.util.AlertNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoticeViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val db = AppDatabase.getDatabase(context)
    private val repository = NoticeRepository(context, db.noticeDao())

    val notices: StateFlow<List<Notice>> = repository.allNotices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastChecked = MutableStateFlow(repository.getLastCheckedTime())
    val lastChecked: StateFlow<Long> = _lastChecked.asStateFlow()

    private val _isBackgroundEnabled = MutableStateFlow(repository.isBackgroundEnabled())
    val isBackgroundEnabled: StateFlow<Boolean> = _isBackgroundEnabled.asStateFlow()

    private val _enabledCategories = MutableStateFlow(repository.getNotificationCategories())
    val enabledCategories: StateFlow<Set<String>> = _enabledCategories.asStateFlow()

    private val _checkIntervalHours = MutableStateFlow(repository.getCheckIntervalHours())
    val checkIntervalHours: StateFlow<Int> = _checkIntervalHours.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // Automatically schedule WorkManager on app startup if background scanning is enabled
        if (repository.isBackgroundEnabled()) {
            WorkScheduler.schedulePeriodicScan(context, repository.getCheckIntervalHours())
        } else {
            WorkScheduler.cancelPeriodicScan(context)
        }
        
        // Trigger an initial scan if database is empty to populate the archive immediately
        viewModelScope.launch {
            val currentList = notices.value
            if (currentList.isEmpty()) {
                Log.d("NoticeViewModel", "Database empty on startup. Triggering initial background scan.")
                triggerManualScan()
            }
        }
    }

    fun triggerManualScan() {
        viewModelScope.launch {
            _isScanning.value = true
            _errorMessage.value = null
            
            val result = repository.refreshNotices { newNotices ->
                // Play notification for newly found notices immediately
                for (notice in newNotices) {
                    AlertNotificationManager.sendNewNoticeNotification(context, notice)
                }
            }

            if (result.isSuccess) {
                _lastChecked.value = repository.getLastCheckedTime()
                val newlyAdded = result.getOrNull() ?: emptyList()
                if (newlyAdded.isNotEmpty()) {
                    _errorMessage.value = "New updates found! Title: ${newlyAdded.first().title}"
                } else {
                    _errorMessage.value = "No updates found on recent scan."
                }
                Log.d("NoticeViewModel", "Manual scan complete. Found ${newlyAdded.size} new notices.")
            } else {
                val error = result.exceptionOrNull()
                _errorMessage.value = error?.message ?: "Unknown scanning error occurred. Please verify your internet connection."
            }
            _isScanning.value = false
        }
    }

    fun toggleBackgroundScan(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBackgroundEnabled(enabled)
            _isBackgroundEnabled.value = enabled
            if (enabled) {
                WorkScheduler.forceSchedulePeriodicScan(context, repository.getCheckIntervalHours())
            } else {
                WorkScheduler.cancelPeriodicScan(context)
            }
        }
    }

    fun updateCategories(categories: Set<String>) {
        viewModelScope.launch {
            repository.setNotificationCategories(categories)
            _enabledCategories.value = categories
        }
    }

    fun updateCheckInterval(hours: Int) {
        viewModelScope.launch {
            repository.setCheckIntervalHours(hours)
            _checkIntervalHours.value = hours
            if (_isBackgroundEnabled.value) {
                WorkScheduler.forceSchedulePeriodicScan(context, hours)
            }
        }
    }

    fun markAsRead(id: String) {
        viewModelScope.launch {
            repository.markAsRead(id)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repository.markAllAsRead()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
            _lastChecked.value = 0L
        }
    }

    fun triggerTestAlert() {
        AlertNotificationManager.triggerTestAlarm(context)
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
