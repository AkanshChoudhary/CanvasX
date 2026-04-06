package com.my_app.art_collab.di

import android.content.Context
import com.my_app.art_collab.engine.BlendModeProcessor
import com.my_app.art_collab.engine.DirtyFlagTracker
import com.my_app.art_collab.engine.EffectProcessor
import com.my_app.art_collab.engine.RenderCache
import com.my_app.art_collab.engine.RenderEngine
import com.my_app.art_collab.engine.TransformProcessor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideEffectProcessor(): EffectProcessor = EffectProcessor()

    @Provides
    @Singleton
    fun provideBlendModeProcessor(): BlendModeProcessor = BlendModeProcessor()

    @Provides
    @Singleton
    fun provideTransformProcessor(): TransformProcessor = TransformProcessor()

    @Provides
    @Singleton
    fun provideDirtyFlagTracker(): DirtyFlagTracker = DirtyFlagTracker()

    @Provides
    @Singleton
    fun provideRenderCache(): RenderCache = RenderCache()

    @Provides
    @Singleton
    fun provideRenderEngine(
        @ApplicationContext context: Context,
        effectProcessor: EffectProcessor,
        blendModeProcessor: BlendModeProcessor,
        transformProcessor: TransformProcessor,
        dirtyFlagTracker: DirtyFlagTracker,
        renderCache: RenderCache
    ): RenderEngine = RenderEngine(
        context,
        effectProcessor,
        blendModeProcessor,
        transformProcessor,
        dirtyFlagTracker,
        renderCache
    )
}
