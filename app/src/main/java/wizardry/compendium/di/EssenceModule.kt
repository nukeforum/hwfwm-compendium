package wizardry.compendium.di

import wizardry.compendium.AwakeningStoneRepository
import wizardry.compendium.EssenceRepository
import wizardry.compendium.essences.AwakeningStoneProvider
import wizardry.compendium.essences.ContributionsToggleFlow
import wizardry.compendium.essences.EssenceProvider
import wizardry.compendium.preferences.PreferencesRepository
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
