package wizardry.compendium.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.CharacterBuildRepository
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.wire.share.BuildShareDecoder
import javax.inject.Singleton

/**
 * Hilt bindings for `:wire` types that can't be `@Inject`-discovered
 * because `:wire` is a JVM-only module without Hilt support.
 *
 * `BuildShareDecoder` is the only such type left — `WireExporter` and
 * `WireImporter` are now owned by `WireIoRepository` in `:wire-repo`
 * and discovered via `@Inject constructor`.
 */
@Module
@InstallIn(SingletonComponent::class)
object WireModule {
    @Singleton
    @Provides
    fun provideBuildShareDecoder(
        essenceRepository: EssenceRepository,
        abilityListingRepository: AbilityListingRepository,
        buildRepository: CharacterBuildRepository,
    ): BuildShareDecoder = BuildShareDecoder(
        essenceRepository = essenceRepository,
        abilityListingRepository = abilityListingRepository,
        buildRepository = buildRepository,
    )
}
