package wizardry.compendium.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import wizardry.compendium.DefaultAbilityListingRepository
import wizardry.compendium.DefaultAwakeningStoneRepository
import wizardry.compendium.DefaultCharacterBuildRepository
import wizardry.compendium.DefaultEssenceRepository
import wizardry.compendium.DefaultStatusEffectRepository
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.CharacterBuildRepository
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.StatusEffectRepository

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
    fun bindAbilityListingRepository(impl: DefaultAbilityListingRepository): AbilityListingRepository

    @Singleton
    @Binds
    fun bindStatusEffectRepository(impl: DefaultStatusEffectRepository): StatusEffectRepository

    @Singleton
    @Binds
    fun bindCharacterBuildRepository(impl: DefaultCharacterBuildRepository): CharacterBuildRepository
}
