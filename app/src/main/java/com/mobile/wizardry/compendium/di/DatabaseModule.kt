package com.mobile.wizardry.compendium.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.mobile.wizardry.compendium.persistence.Canonical
import com.mobile.wizardry.compendium.persistence.CompendiumDatabase
import com.mobile.wizardry.compendium.persistence.CompositeEssenceCache
import com.mobile.wizardry.compendium.persistence.Contributions
import com.mobile.wizardry.compendium.persistence.ContributionsToggle
import com.mobile.wizardry.compendium.persistence.DatabaseEssenceCache
import com.mobile.wizardry.compendium.persistence.EssenceCache
import com.mobile.wizardry.compendium.persistence.EssenceDatabase
import com.mobile.wizardry.compendium.preferences.PreferencesRepository
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
    }
}
