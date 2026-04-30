package wizardry.compendium.di

import wizardry.compendium.DefaultAwakeningStoneRepository
import wizardry.compendium.DefaultEssenceRepository
import wizardry.compendium.essences.AwakeningStoneContributionsToggleFlow
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.EssenceContributionsToggleFlow
import wizardry.compendium.essences.EssenceRepository
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
    fun bindEssenceRepository(impl: DefaultEssenceRepository): EssenceRepository

    @Singleton
    @Binds
    fun bindAwakeningStoneRepository(impl: DefaultAwakeningStoneRepository): AwakeningStoneRepository

    @Singleton
    @Binds
    fun bindEssenceContributionsToggleFlow(impl: PreferencesRepository): EssenceContributionsToggleFlow

    @Singleton
    @Binds
    fun bindAwakeningStoneContributionsToggleFlow(impl: PreferencesRepository): AwakeningStoneContributionsToggleFlow
}
