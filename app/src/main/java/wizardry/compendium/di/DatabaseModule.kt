package wizardry.compendium.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
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
import wizardry.compendium.persistence.CharacterBuildDatabase
import wizardry.compendium.persistence.CompendiumDatabase
import wizardry.compendium.persistence.Contributions
import wizardry.compendium.persistence.EssenceCache
import wizardry.compendium.persistence.EssenceDatabase
import wizardry.compendium.persistence.StatusEffectCache
import wizardry.compendium.persistence.StatusEffectDatabase

/**
 * Single SQLite driver per database file. Every per-entity *Database is built
 * on top of the shared @Canonical / @Contributions [SqlDriver] so writes
 * through one cache are immediately visible to reads on any other cache that
 * shares the same file. Previously each cache constructed its own
 * AndroidSqliteDriver against the same physical file, which fragmented the
 * connection pool, lost intra-process snapshot-after-commit guarantees, and
 * risked SQLITE_BUSY contention under cross-cache write/read interleavings.
 *
 * EssenceDatabase is provided once per file and exposed both as the
 * [EssenceCache] interface (read-only callers) and as the concrete
 * [EssenceDatabase] (callers that need the richer
 * confluenceNamesReferencingEssenceRef etc. surface).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    @Canonical
    fun provideCanonicalDriver(@ApplicationContext context: Context): SqlDriver =
        AndroidSqliteDriver(CompendiumDatabase.Schema, context, "compendium.db")

    @Provides
    @Singleton
    @Contributions
    fun provideContributionsDriver(@ApplicationContext context: Context): SqlDriver =
        AndroidSqliteDriver(CompendiumDatabase.Schema, context, "contributions.db")

    // ── Canonical ────────────────────────────────────────────────────────

    @Provides
    @Singleton
    @Canonical
    fun provideCanonicalEssenceCache(@Canonical driver: SqlDriver): EssenceCache =
        EssenceDatabase(driver)

    @Provides
    @Singleton
    @Canonical
    fun provideCanonicalAwakeningStoneCache(@Canonical driver: SqlDriver): AwakeningStoneCache =
        AwakeningStoneDatabase(driver)

    @Provides
    @Singleton
    @Canonical
    fun provideCanonicalAbilityListingCache(@Canonical driver: SqlDriver): AbilityListingCache =
        AbilityListingDatabase(driver)

    @Provides
    @Singleton
    @Canonical
    fun provideCanonicalStatusEffectCache(@Canonical driver: SqlDriver): StatusEffectCache =
        StatusEffectDatabase(driver)

    // ── Contributions ────────────────────────────────────────────────────

    @Provides
    @Singleton
    @Contributions
    fun provideContributionsEssenceDatabase(@Contributions driver: SqlDriver): EssenceDatabase =
        EssenceDatabase(driver)

    @Provides
    @Singleton
    @Contributions
    fun provideContributionsEssenceCache(@Contributions db: EssenceDatabase): EssenceCache = db

    @Provides
    @Singleton
    @Contributions
    fun provideContributionsAwakeningStoneCache(@Contributions driver: SqlDriver): AwakeningStoneCache =
        AwakeningStoneDatabase(driver)

    @Provides
    @Singleton
    @Contributions
    fun provideContributionsAbilityListingCache(@Contributions driver: SqlDriver): AbilityListingCache =
        AbilityListingDatabase(driver)

    @Provides
    @Singleton
    @Contributions
    fun provideContributionsStatusEffectCache(@Contributions driver: SqlDriver): StatusEffectCache =
        StatusEffectDatabase(driver)

    @Provides
    @Singleton
    @Contributions
    fun provideContributionsCharacterBuildDatabase(@Contributions driver: SqlDriver): CharacterBuildDatabase =
        CharacterBuildDatabase(driver)
}
