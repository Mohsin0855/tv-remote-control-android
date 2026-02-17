package com.example.tvremotetest.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remote_new")
data class RemoteButton(
    @PrimaryKey
    @ColumnInfo(name = "index")
    val id: Int,

    @ColumnInfo(name = "_id")
    val remoteId: Int?,

    @ColumnInfo(name = "remote_fragments")
    val remoteFragments: String?,

    @ColumnInfo(name = "remote_button_fragments")
    val buttonName: String?,

    @ColumnInfo(name = "device_category")
    val deviceCategory: String?,

    @ColumnInfo(name = "brand_model")
    val brandModel: String?,

    @ColumnInfo(name = "frequency")
    val frequency: Int?,

    @ColumnInfo(name = "ir_remote_frame")
    val irRemoteFrame: String?
)
