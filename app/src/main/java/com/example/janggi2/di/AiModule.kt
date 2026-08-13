package com.example.janggi2.di

import com.example.janggi2.data.ai.FairyStockfishEngine
import com.example.janggi2.data.ai.FenConverter
import com.example.janggi2.data.ai.NotationConverter
import com.example.janggi2.data.ai.UciProtocol
import com.example.janggi2.domain.ai.AiEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing AI-related dependencies.
 *
 * All AI components are provided as singletons to ensure:
 * 1. Single native engine instance (resource efficiency)
 * 2. Consistent state across the app
 * 3. Proper lifecycle management
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    /**
     * Provides the notation converter for UCI ↔ Position conversion.
     */
    @Provides
    @Singleton
    fun provideNotationConverter(): NotationConverter {
        return NotationConverter()
    }

    /**
     * Provides the FEN builder used to describe the board to the engine.
     */
    @Provides
    @Singleton
    fun provideFenConverter(): FenConverter {
        return FenConverter()
    }

    /**
     * Provides the UCI protocol formatter.
     */
    @Provides
    @Singleton
    fun provideUciProtocol(
        fenConverter: FenConverter
    ): UciProtocol {
        return UciProtocol(fenConverter)
    }

    /**
     * Provides the AI engine implementation.
     *
     * This is a singleton to ensure only one native engine instance exists.
     * The engine must be initialized before use via InitializeAiUseCase.
     */
    @Provides
    @Singleton
    fun provideAiEngine(
        notationConverter: NotationConverter,
        uciProtocol: UciProtocol
    ): AiEngine {
        return FairyStockfishEngine(notationConverter, uciProtocol)
    }
}
