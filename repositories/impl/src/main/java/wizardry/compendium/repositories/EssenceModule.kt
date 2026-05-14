package wizardry.compendium.repositories

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.AwakeningStoneRepository
import wizardry.compendium.repositories.CharacterBuildRepository
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.repositories.StatusEffectRepository

@Module
@InstallIn(SingletonComponent::class)
internal interface EssenceModule {
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
