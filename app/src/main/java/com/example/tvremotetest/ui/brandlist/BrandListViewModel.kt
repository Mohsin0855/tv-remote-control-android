package com.example.tvremotetest.ui.brandlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.example.tvremotetest.data.RemoteDatabase
import com.example.tvremotetest.data.RemoteRepository
import com.example.tvremotetest.data.entity.BrandDevice

class BrandListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RemoteRepository

    val brandsWithCategory: LiveData<List<BrandDevice>>

    init {
        val dao = RemoteDatabase.getInstance(application).remoteDao()
        repository = RemoteRepository(dao)
        brandsWithCategory = repository.getDistinctBrandsWithCategory()
    }
}
