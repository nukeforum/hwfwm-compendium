package wizardry.compendium.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.wire.WireExporter
import javax.inject.Singleton

/**
 * Hilt bindings for the `:wire` module.
 *
 * `WireExporter` is a plain Kotlin class (no `@Inject` annotation) because
 * `:wire` is a JVM-only module that doesn't pull in Hilt/javax.inject. We
 * wrap its construction here so consumers (the detail VM, the share VM,
 * the settings VM, etc.) can inject it directly rather than re-deriving
 * the four-repo constructor at every callsite.
 */
@Module
@InstallIn(SingletonComponent::class)
object WireModule {
    @Singleton
    @Provides
    fun provideWireExporter(
        essenceRepository: EssenceRepository,
        awakeningStoneRepository: AwakeningStoneRepository,
        abilityListingRepository: AbilityListingRepository,
        statusEffectRepository: StatusEffectRepository,
    ): WireExporter = WireExporter(
        essenceRepository = essenceRepository,
        awakeningStoneRepository = awakeningStoneRepository,
        abilityListingRepository = abilityListingRepository,
        statusEffectRepository = statusEffectRepository,
    )
}
