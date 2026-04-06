package com.my_app.art_collab.di

import android.content.Context
import androidx.room.Room
import com.my_app.art_collab.data.local.db.CanvasXDatabase
import com.my_app.art_collab.data.local.db.dao.CanvasDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CanvasXDatabase {
        return Room.databaseBuilder(
            context,
            CanvasXDatabase::class.java,
            "canvasx_database"
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideCanvasDao(database: CanvasXDatabase): CanvasDao {
        return database.canvasDao()
    }
}
