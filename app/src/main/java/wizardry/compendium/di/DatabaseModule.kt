package wizardry.compendium.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import wizardry.compendium.persistence.AbilityListingCache
import wizardry.compendium.persistence.AbilityListingDatabase
import wizardry.compendium.persistence.AwakeningStoneCache
import wizardry.compendium.persistence.AwakeningStoneDatabase
import wizardry.compendium.persistence.Canonical
import wizardry.compendium.persistence.CharacterBuildCache
import wizardry.compendium.persistence.CharacterBuildDatabase
import wizardry.compendium.persistence.CompendiumDatabase
import wizardry.compendium.persistence.Contributions
import wizardry.compendium.persistence.DatabaseAbilityListingCache
import wizardry.compendium.persistence.DatabaseAwakeningStoneCache
import wizardry.compendium.persistence.DatabaseCharacterBuildCache
import wizardry.compendium.persistence.DatabaseEssenceCache
import wizardry.compendium.persistence.DatabaseStatusEffectCache
import wizardry.compendium.persistence.EssenceCache
import wizardry.compendium.persistence.EssenceDatabase
import wizardry.compendium.persistence.StatusEffectCache
import wizardry.compendium.persistence.StatusEffectDatabase

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {

    companion object {
        @Provides
        @Singleton
        @Canonical
        fun provideCanonicalEssenceCache(@ApplicationContext context: Context): EssenceCache =
            DatabaseEssenceCache(
                EssenceDatabase(
                    AndroidSqliteDriver(CompendiumDatabase.Schema, context, "compendium.db")
                )
            )

        @Provides
        @Singleton
        @Contributions
        fun provideContributionsEssenceCache(@ApplicationContext context: Context): EssenceCache =
            DatabaseEssenceCache(
                EssenceDatabase(
                    AndroidSqliteDriver(CompendiumDatabase.Schema, context, "contributions.db")
                )
            )

        @Provides
        @Singleton
        @Canonical
        fun provideCanonicalAwakeningStoneCache(@ApplicationContext context: Context): AwakeningStoneCache =
            DatabaseAwakeningStoneCache(
                AwakeningStoneDatabase(
                    AndroidSqliteDriver(CompendiumDatabase.Schema, context, "compendium.db")
                )
            )

        @Provides
        @Singleton
        @Contributions
        fun provideContributionsAwakeningStoneCache(@ApplicationContext context: Context): AwakeningStoneCache =
            DatabaseAwakeningStoneCache(
                AwakeningStoneDatabase(
                    AndroidSqliteDriver(CompendiumDatabase.Schema, context, "contributions.db")
                )
            )

        @Provides
        @Singleton
        @Canonical
        fun provideCanonicalAbilityListingCache(@ApplicationContext context: Context): AbilityListingCache =
            DatabaseAbilityListingCache(
                AbilityListingDatabase(
                    AndroidSqliteDriver(CompendiumDatabase.Schema, context, "compendium.db")
                )
            )

        @Provides
        @Singleton
        @Contributions
        fun provideContributionsAbilityListingCache(@ApplicationContext context: Context): AbilityListingCache =
            DatabaseAbilityListingCache(
                AbilityListingDatabase(
                    AndroidSqliteDriver(CompendiumDatabase.Schema, context, "contributions.db")
                )
            )

        @Provides
        @Singleton
        @Canonical
        fun provideCanonicalStatusEffectCache(@ApplicationContext context: Context): StatusEffectCache =
            DatabaseStatusEffectCache(
                StatusEffectDatabase(
                    AndroidSqliteDriver(CompendiumDatabase.Schema, context, "compendium.db")
                )
            )

        @Provides
        @Singleton
        @Contributions
        fun provideContributionsStatusEffectCache(@ApplicationContext context: Context): StatusEffectCache =
            DatabaseStatusEffectCache(
                StatusEffectDatabase(
                    AndroidSqliteDriver(CompendiumDatabase.Schema, context, "contributions.db")
                )
            )

        @Provides
        @Singleton
        @Contributions
        fun provideContributionsCharacterBuildCache(@ApplicationContext context: Context): CharacterBuildCache =
            DatabaseCharacterBuildCache(
                CharacterBuildDatabase(
                    AndroidSqliteDriver(CompendiumDatabase.Schema, context, "contributions.db")
                )
            )
    }
}
