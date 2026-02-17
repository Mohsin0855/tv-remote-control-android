package com.example.tvremotetest.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.tvremotetest.data.dao.RemoteDao
import com.example.tvremotetest.data.entity.RemoteButton

@Database(entities = [RemoteButton::class], version = 1, exportSchema = false)
abstract class RemoteDatabase : RoomDatabase() {

    abstract fun remoteDao(): RemoteDao

    companion object {
        @Volatile
        private var INSTANCE: RemoteDatabase? = null

        fun getInstance(context: Context): RemoteDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RemoteDatabase::class.java,
                    "remote_database"
                )
                    .createFromAsset("database/remote.db")
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
