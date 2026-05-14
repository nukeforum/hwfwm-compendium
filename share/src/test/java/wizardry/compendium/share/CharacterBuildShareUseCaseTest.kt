package wizardry.compendium.share

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.wire.share.BuildShareDecoder

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterBuildShareUseCaseTest {

    private fun newUseCase(): CharacterBuildShareUseCase {
        val wireIo = stubWireIoRepository()
        val decoder = BuildShareDecoder(
            essenceRepository = StubEssenceRepo,
            abilityListingRepository = StubListingRepo,
            buildRepository = StubBuildRepo,
        )
        return CharacterBuildShareUseCase(wireIo = wireIo, buildShareDecoder = decoder)
    }

    @Test
    fun `encode then decodeBuildBundle round-trips`() = runTest {
        val useCase = newUseCase()
        val build = CharacterBuild(name = "Frosty", race = "Human", racialAbilities = emptyList())

        val encoded = useCase.encode(build)
        val result = useCase.decodeBuildBundle(encoded)

        assertTrue("expected Loaded, got $result", result is DecodedSingle.Loaded)
        val preview = (result as DecodedSingle.Loaded).model
        assertEquals("Frosty", preview.originalName)
        assertEquals("Human", preview.race)
    }

    @Test
    fun `renderAsText returns plain-text rendering`() {
        val useCase = newUseCase()
        val build = CharacterBuild(name = "Frosty", race = "Human", racialAbilities = emptyList())

        val text = useCase.renderAsText(build, statusEffects = emptyList())

        assertTrue("text was: $text", text.startsWith("Frosty\nHuman\n"))
    }
}
