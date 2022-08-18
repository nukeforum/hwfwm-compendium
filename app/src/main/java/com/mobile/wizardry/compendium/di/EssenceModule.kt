package com.mobile.wizardry.compendium.di

import com.mobile.wizardry.compendium.EssenceRepository
import com.mobile.wizardry.compendium.essences.EssenceProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface EssenceModule {
    @Singleton
    @Binds
    fun getEssenceProvider(provider: EssenceRepository): EssenceProvider
}
