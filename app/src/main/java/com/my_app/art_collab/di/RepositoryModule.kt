package com.my_app.art_collab.di

import com.my_app.art_collab.data.repository.AuthRepositoryImpl
import com.my_app.art_collab.data.repository.CanvasRepositoryImpl
import com.my_app.art_collab.data.repository.RealtimeDBRepositoryImpl
import com.my_app.art_collab.data.repository.StorageRepositoryImpl
import com.my_app.art_collab.domain.repository.AuthRepository
import com.my_app.art_collab.domain.repository.CanvasRepository
import com.my_app.art_collab.domain.repository.RealtimeDBRepository
import com.my_app.art_collab.domain.repository.StorageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindCanvasRepository(
        canvasRepositoryImpl: CanvasRepositoryImpl
    ): CanvasRepository

    @Binds
    @Singleton
    abstract fun bindStorageRepository(storageRepositoryImpl: StorageRepositoryImpl): StorageRepository


    @Binds
    @Singleton
    abstract fun bindRealtimeDBRepository(realtimeDBRepositoryImpl: RealtimeDBRepositoryImpl): RealtimeDBRepository
}
