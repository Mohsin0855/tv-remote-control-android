package com.example.tvremotetest.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import com.example.tvremotetest.data.entity.BrandDevice
import com.example.tvremotetest.data.entity.RemoteButton

@Dao
interface RemoteDao {

    @Query(
        """
        SELECT DISTINCT brand_model AS brandModel, device_category AS deviceCategory 
        FROM remote_new 
        ORDER BY brand_model ASC
        """
    )
    fun getDistinctBrandsWithCategory(): LiveData<List<BrandDevice>>

    @Query(
        """
        SELECT DISTINCT remote_fragments 
        FROM remote_new 
        WHERE brand_model = :brand AND device_category = :category 
        ORDER BY remote_fragments ASC
        """
    )
    fun getRemoteFragments(brand: String, category: String): LiveData<List<String>>

    @Query(
        """
        SELECT * FROM remote_new 
        WHERE remote_fragments = :fragment 
        ORDER BY `index` ASC
        """
    )
    fun getButtonsForFragment(fragment: String): LiveData<List<RemoteButton>>
}
