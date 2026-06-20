package com.example.janggi2.di

import com.example.janggi2.data.imageprocessing.GridDetector
import com.example.janggi2.data.repository.BoardImportRepositoryImpl
import com.example.janggi2.data.repository.GameRepositoryImpl
import com.example.janggi2.domain.repository.BoardImportRepository
import com.example.janggi2.domain.repository.GameRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing repository implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGameRepository(
        impl: GameRepositoryImpl
    ): GameRepository

    @Binds
    @Singleton
    abstract fun bindBoardImportRepository(
        impl: BoardImportRepositoryImpl
    ): BoardImportRepository

    companion object {
        @Provides
        @Singleton
        fun provideGridDetector(): GridDetector {
            return GridDetector()
        }
    }
}
