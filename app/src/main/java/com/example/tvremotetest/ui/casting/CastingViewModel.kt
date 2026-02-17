package com.example.tvremotetest.ui.casting

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.tvremotetest.casting.CastingRepository
import com.example.tvremotetest.casting.ScreenCaptureService

class CastingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CastingRepository(application)

    private val _isStreaming = MutableLiveData(false)
    val isStreaming: LiveData<Boolean> = _isStreaming

    private val _streamUrl = MutableLiveData<String?>()
    val streamUrl: LiveData<String?> = _streamUrl

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val stateCallback: (Boolean) -> Unit = { running ->
        _isStreaming.postValue(running)
        if (running) {
            _streamUrl.postValue(repository.getStreamUrl())
        } else {
            _streamUrl.postValue(null)
        }
    }

    init {
        ScreenCaptureService.onStateChanged.add(stateCallback)
        _isStreaming.value = repository.isStreaming()
        if (repository.isStreaming()) {
            _streamUrl.value = repository.getStreamUrl()
        }
    }

    fun prepareStreamUrl(): Boolean {
        val url = repository.getStreamUrl()
        if (url == null) {
            _errorMessage.value = "Not connected to WiFi. Please connect to a WiFi network."
            return false
        }
        _streamUrl.value = url
        return true
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        ScreenCaptureService.onStateChanged.remove(stateCallback)
    }
}
