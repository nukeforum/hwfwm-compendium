package wizardry.compendium.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import wizardry.compendium.persistence.AwakeningStoneCache
import wizardry.compendium.persistence.AwakeningStoneDatabase
import wizardry.compendium.persistence.Canonical
import wizardry.compendium.persistence.CompendiumDatabase
import wizardry.compendium.persistence.CompositeEssenceCache
import wizardry.compendium.persistence.Contributions
import wizardry.compendium.persistence.ContributionsToggle
import wizardry.compendium.persistence.DatabaseAwakeningStoneCache
import wizardry.compendium.persistence.DatabaseEssenceCache
import wizardry.compendium.persistence.EssenceCache
import wizardry.compendium.persistence.EssenceDatabase
import wizardry.compendium.preferences.PreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {

    @Binds
    @Singleton
    abstract fun bindEssenceCache(impl: CompositeEssenceCache): EssenceCache

    @Binds
    @Singleton
    abstract fun bindContributionsToggle(impl: PreferencesRepository): ContributionsToggle

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
        fun provideAwakeningStoneCache(@ApplicationContext context: Context): AwakeningStoneCache =
            DatabaseAwakeningStoneCache(
                AwakeningStoneDatabase(
                    AndroidSqliteDriver(CompendiumDatabase.Schema, context, "compendium.db")
                )
            )
    }
}
