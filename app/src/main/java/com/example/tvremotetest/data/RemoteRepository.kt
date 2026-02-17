package com.example.tvremotetest.data

import androidx.lifecycle.LiveData
import com.example.tvremotetest.data.dao.RemoteDao
import com.example.tvremotetest.data.entity.BrandDevice
import com.example.tvremotetest.data.entity.RemoteButton

class RemoteRepository(private val remoteDao: RemoteDao) {

    fun getDistinctBrandsWithCategory(): LiveData<List<BrandDevice>> {
        return remoteDao.getDistinctBrandsWithCategory()
    }

    fun getRemoteFragments(brand: String, category: String): LiveData<List<String>> {
        return remoteDao.getRemoteFragments(brand, category)
    }

    fun getButtonsForFragment(fragment: String): LiveData<List<RemoteButton>> {
        return remoteDao.getButtonsForFragment(fragment)
    }
}
