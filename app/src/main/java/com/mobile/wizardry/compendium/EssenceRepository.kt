package com.mobile.wizardry.compendium

import com.mobile.wizardry.compendium.essences.EssenceProvider
import com.mobile.wizardry.compendium.essences.dataloader.EssenceDataLoader
import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.persistence.CompendiumDb
import com.mobile.wizardry.compendium.persistence.EssenceCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EssenceRepository (
    private val essenceDataLoader: EssenceDataLoader,
    private val cache: EssenceCache,
    private val db: CompendiumDb,
    private val dispatcher: CoroutineDispatcher,
) : EssenceProvider {
    @Inject constructor(
        essenceDataLoader: EssenceDataLoader,
        cache: EssenceCache,
        db: CompendiumDb,
    ) : this(
        essenceDataLoader,
                cache,
                db,
        Dispatchers.IO,
    )

    private val scope = CoroutineScope(dispatcher)

    override suspend fun getEssences(): List<Essence> {
        return cache.contents.takeIf { it.isNotEmpty() }
            ?: essenceDataLoader.loadEssenceData()
                .also { cache.contents = it }
    }

    private fun storeInDb(essences: List<Essence>) {
        scope.launch {
            essences.forEach {
                db.essenceQueries.
            }
        }
    }
}
