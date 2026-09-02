package com.example.janggi2.di

import android.content.Context
import androidx.room.Room
import com.example.janggi2.data.local.database.JangGiDatabase
import com.example.janggi2.data.local.database.dao.GameCommentDao
import com.example.janggi2.data.local.database.dao.GameDao
import com.example.janggi2.data.local.database.dao.GameReviewDao
import com.example.janggi2.data.mapper.GameMapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing database-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideJangGiDatabase(
        @ApplicationContext context: Context
    ): JangGiDatabase {
        return Room.databaseBuilder(
            context,
            JangGiDatabase::class.java,
            "janggi_database"
        )
            .addMigrations(
                JangGiDatabase.MIGRATION_1_2,
                JangGiDatabase.MIGRATION_2_3,
                JangGiDatabase.MIGRATION_3_4,
                JangGiDatabase.MIGRATION_4_5,
                JangGiDatabase.MIGRATION_5_6,
                JangGiDatabase.MIGRATION_6_7
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideGameDao(database: JangGiDatabase): GameDao {
        return database.gameDao()
    }

    @Provides
    @Singleton
    fun provideGameReviewDao(database: JangGiDatabase): GameReviewDao {
        return database.gameReviewDao()
    }

    @Provides
    @Singleton
    fun provideGameCommentDao(database: JangGiDatabase): GameCommentDao {
        return database.gameCommentDao()
    }

    @Provides
    @Singleton
    fun provideGameMapper(): GameMapper {
        return GameMapper()
    }
}
