package com.mobile.wizardry.compendium.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.mobile.wizardry.compendium.persistence.CompendiumDatabase
import com.mobile.wizardry.compendium.persistence.DatabaseEssenceCache
import com.mobile.wizardry.compendium.persistence.EssenceCache
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
    abstract fun bindEssenceCache(impl: DatabaseEssenceCache): EssenceCache

    companion object {
        @Provides
        @Singleton
        fun provideSqlDriver(@ApplicationContext context: Context): SqlDriver {
            return AndroidSqliteDriver(CompendiumDatabase.Schema, context, "compendium.db")
        }
    }
}
