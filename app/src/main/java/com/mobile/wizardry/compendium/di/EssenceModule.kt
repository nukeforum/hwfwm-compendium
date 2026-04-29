package com.mobile.wizardry.compendium.di

import com.mobile.wizardry.compendium.AwakeningStoneRepository
import com.mobile.wizardry.compendium.EssenceRepository
import com.mobile.wizardry.compendium.essences.AwakeningStoneProvider
import com.mobile.wizardry.compendium.essences.ContributionsToggleFlow
import com.mobile.wizardry.compendium.essences.EssenceProvider
import com.mobile.wizardry.compendium.preferences.PreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface EssenceModule {
    @Singleton
    @Binds
    fun getEssenceProvider(provider: EssenceRepository): EssenceProvider

    @Singleton
    @Binds
    fun bindContributionsToggleFlow(impl: PreferencesRepository): ContributionsToggleFlow

    @Singleton
    @Binds
    fun getAwakeningStoneProvider(provider: AwakeningStoneRepository): AwakeningStoneProvider
}
