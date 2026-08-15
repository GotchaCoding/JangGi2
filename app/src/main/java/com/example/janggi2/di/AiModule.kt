package com.example.janggi2.di

import com.example.janggi2.data.ai.FairyStockfishEngine
import com.example.janggi2.data.ai.FenConverter
import com.example.janggi2.data.ai.NotationConverter
import com.example.janggi2.data.ai.UciProtocol
import com.example.janggi2.domain.ai.AiEngine
import com.example.janggi2.domain.rules.RepetitionJudge
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
        fenConverter: FenConverter,
        notationConverter: NotationConverter
    ): UciProtocol {
        return UciProtocol(fenConverter, notationConverter)
    }

    /**
     * Provides the one and only native engine.
     *
     * 이 앱의 네이티브 엔진은 하나뿐이어야 합니다. 아래 두 인터페이스는 **같은 인스턴스**를
     * 가리키는 창구일 뿐입니다 - 각각 따로 provide 하면 네이티브 핸들이 둘 생깁니다.
     * The engine must be initialized before use via InitializeAiUseCase.
     */
    @Provides
    @Singleton
    fun provideFairyStockfishEngine(
        notationConverter: NotationConverter,
        uciProtocol: UciProtocol
    ): FairyStockfishEngine {
        return FairyStockfishEngine(notationConverter, uciProtocol)
    }

    /** 탐색(AI 착수·힌트) 창구 */
    @Provides
    @Singleton
    fun provideAiEngine(engine: FairyStockfishEngine): AiEngine = engine

    /** 반복 규칙 판정 창구 */
    @Provides
    @Singleton
    fun provideRepetitionJudge(engine: FairyStockfishEngine): RepetitionJudge = engine
}
