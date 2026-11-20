package wizardry.compendium.repositories

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.essences.dataloader.EssenceDataLoader
import wizardry.compendium.persistence.CharacterBuildDatabase
import wizardry.compendium.persistence.CompendiumDatabase
import wizardry.compendium.persistence.EssenceDatabase
import wizardry.compendium.preferences.EssenceContributionsToggle
import wizardry.compendium.preferences.EssenceContributionsToggleFlow

/**
 * The first read of the canonical essences loads them into an empty cache.
 * These tests drive [DefaultEssenceRepository.readCanonical] on the test
 * scheduler, so "a second caller arrives while the first is still loading" is
 * an exact interleaving and not a matter of thread timing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultEssenceRepositoryCanonicalLoadTest {

    @Test
    fun `canonical essences are loaded once when a second caller arrives before the first load finishes`() = runTest {
        val loader = GatedEssenceDataLoader(listOf(flame))
        val repo = repositoryWithEmptyCanonicalCache(loader)

        val first = async { repo.readCanonical() }
        runCurrent() // first is suspended inside the loader
        val second = async { repo.readCanonical() }
        runCurrent() // second has gone as far as it can
        loader.finishLoading()

        assertEquals(listOf(flame), first.await())
        assertEquals(listOf(flame), second.await())
        assertEquals(1, loader.loadCount)
    }

    @Test
    fun `an empty canonical data set is still loaded only once`() = runTest {
        val loader = GatedEssenceDataLoader(emptyList())
        val repo = repositoryWithEmptyCanonicalCache(loader)

        val first = async { repo.readCanonical() }
        runCurrent()
        val second = async { repo.readCanonical() }
        runCurrent()
        loader.finishLoading()

        assertEquals(emptyList<Essence>(), first.await())
        assertEquals(emptyList<Essence>(), second.await())
        assertEquals(1, loader.loadCount)
    }

    @Test
    fun `a failed first load is not remembered and the next caller loads again`() = runTest {
        val loader = GatedEssenceDataLoader(listOf(flame))
        val repo = repositoryWithEmptyCanonicalCache(loader)

        val first = async { runCatching { repo.readCanonical() } }
        runCurrent()
        loader.failLoading(IllegalStateException("asset unreadable"))
        assertTrue(first.await().isFailure)

        val second = async { repo.readCanonical() }
        runCurrent()
        loader.finishLoading()

        assertEquals(listOf(flame), second.await())
        assertEquals(2, loader.loadCount)
    }
}

private val flame: Essence =
    Essence.of(name = "Flame", description = "", rarity = Rarity.Common, restricted = false)

/** Each load waits until the test finishes or fails it, and is counted. */
private class GatedEssenceDataLoader(private val data: List<Essence>) : EssenceDataLoader {
    var loadCount = 0
        private set
    private var gate = CompletableDeferred<Unit>()

    override suspend fun loadEssenceData(): List<Essence> {
        loadCount++
        gate.await()
        return data
    }

    fun finishLoading() {
        gate.complete(Unit)
    }

    fun failLoading(cause: Exception) {
        gate.completeExceptionally(cause)
        gate = CompletableDeferred()
    }
}

/** Real in-memory databases for both caches; only the loader is a fake. */
private fun repositoryWithEmptyCanonicalCache(loader: EssenceDataLoader): DefaultEssenceRepository {
    val canonicalDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    CompendiumDatabase.Schema.create(canonicalDriver)
    val contributionsDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    CompendiumDatabase.Schema.create(contributionsDriver)
    val contributions = EssenceDatabase(contributionsDriver)
    return DefaultEssenceRepository(
        dataLoader = loader,
        canonicalCache = EssenceDatabase(canonicalDriver),
        contributionsCache = contributions,
        essenceDatabase = contributions,
        characterBuildDatabase = CharacterBuildDatabase(contributionsDriver),
        toggle = object : EssenceContributionsToggle {
            override val isEssenceContributionsEnabled = false
        },
        toggleFlow = object : EssenceContributionsToggleFlow {
            override val essenceContributionsEnabled: Flow<Boolean> = MutableStateFlow(false)
        },
    )
}
