package com.example.tvremotetest.ui.remote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.example.tvremotetest.data.RemoteDatabase
import com.example.tvremotetest.data.RemoteRepository
import com.example.tvremotetest.data.entity.RemoteButton

class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RemoteRepository

    private val _selectedFragment = MutableLiveData<String>()

    val remoteFragments: LiveData<List<String>>

    val buttons: LiveData<List<RemoteButton>> = _selectedFragment.switchMap { fragment ->
        repository.getButtonsForFragment(fragment)
    }

    private var brand: String = ""
    private var category: String = ""

    init {
        val dao = RemoteDatabase.getInstance(application).remoteDao()
        repository = RemoteRepository(dao)
        remoteFragments = MutableLiveData()
    }

    fun loadFragments(brand: String, category: String) {
        this.brand = brand
        this.category = category
        val fragments = repository.getRemoteFragments(brand, category)
        (remoteFragments as MutableLiveData).apply {
            // We'll observe this from the activity and set it
        }
    }

    fun getFragmentsLiveData(brand: String, category: String): LiveData<List<String>> {
        return repository.getRemoteFragments(brand, category)
    }

    fun selectFragment(fragment: String) {
        _selectedFragment.value = fragment
    }
}
