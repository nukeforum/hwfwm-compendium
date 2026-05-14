package wizardry.compendium.preferences

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {

    @Binds @Singleton
    abstract fun bindPreferencesRepository(
        impl: DataStorePreferencesRepository,
    ): PreferencesRepository

    @Binds @Singleton
    abstract fun bindEssenceContributionsToggle(
        impl: DataStorePreferencesRepository,
    ): EssenceContributionsToggle

    @Binds @Singleton
    abstract fun bindAwakeningStoneContributionsToggle(
        impl: DataStorePreferencesRepository,
    ): AwakeningStoneContributionsToggle

    @Binds @Singleton
    abstract fun bindAbilityListingContributionsToggle(
        impl: DataStorePreferencesRepository,
    ): AbilityListingContributionsToggle

    @Binds @Singleton
    abstract fun bindStatusEffectContributionsToggle(
        impl: DataStorePreferencesRepository,
    ): StatusEffectContributionsToggle

    @Binds @Singleton
    abstract fun bindEssencesAsAwakeningStonesToggle(
        impl: DataStorePreferencesRepository,
    ): EssencesAsAwakeningStonesToggle

    @Binds @Singleton
    abstract fun bindEssenceContributionsToggleFlow(
        impl: DataStorePreferencesRepository,
    ): EssenceContributionsToggleFlow

    @Binds @Singleton
    abstract fun bindAwakeningStoneContributionsToggleFlow(
        impl: DataStorePreferencesRepository,
    ): AwakeningStoneContributionsToggleFlow

    @Binds @Singleton
    abstract fun bindAbilityListingContributionsToggleFlow(
        impl: DataStorePreferencesRepository,
    ): AbilityListingContributionsToggleFlow

    @Binds @Singleton
    abstract fun bindStatusEffectContributionsToggleFlow(
        impl: DataStorePreferencesRepository,
    ): StatusEffectContributionsToggleFlow

    @Binds @Singleton
    abstract fun bindEssencesAsAwakeningStonesToggleFlow(
        impl: DataStorePreferencesRepository,
    ): EssencesAsAwakeningStonesToggleFlow
}
