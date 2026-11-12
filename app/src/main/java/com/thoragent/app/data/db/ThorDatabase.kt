package com.thoragent.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, MessageEntity::class, ProviderProfileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ThorDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun providerDao(): ProviderDao

    companion object {
        fun build(context: Context): ThorDatabase =
            Room.databaseBuilder(context.applicationContext, ThorDatabase::class.java, "thor_agent.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
