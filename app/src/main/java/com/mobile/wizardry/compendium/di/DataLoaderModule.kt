package com.mobile.wizardry.compendium.di

import com.mobile.wizardry.compendium.AssetFileStreamSource
import com.mobile.wizardry.compendium.essences.dataloader.EssenceCsvLoader
import com.mobile.wizardry.compendium.essences.dataloader.EssenceDataLoader
import com.mobile.wizardry.compendium.essences.dataloader.FileStreamSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataLoaderModule {
    @Singleton
    @Binds
    abstract fun getFileStreamSource(source: AssetFileStreamSource): FileStreamSource

    @Singleton
    @Binds
    abstract fun getEssenceDataLoader(loader: EssenceCsvLoader): EssenceDataLoader
}
