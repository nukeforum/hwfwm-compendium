package wizardry.compendium.share

import wizardry.compendium.ability.preview.AbilityTextRenderer
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.wire.repo.WireIoRepository
import wizardry.compendium.wire.share.BuildImportPreview
import wizardry.compendium.wire.share.BuildShareDecoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encode, plaintext-render, and decode a single contributed [CharacterBuild]
 * for share intents and paste-import flows. Bundles the three operations the
 * build detail screen and the contribute screen need behind one Hilt-injected
 * type so callers don't reach into [WireIoRepository], [AbilityTextRenderer],
 * or [BuildShareDecoder] directly.
 */
@Singleton
open class CharacterBuildShareUseCase @Inject constructor(
    private val wireIo: WireIoRepository,
    private val buildShareDecoder: BuildShareDecoder,
) {
    open fun encode(build: CharacterBuild): String = wireIo.encodeSingle(build)

    open fun renderAsText(build: CharacterBuild, statusEffects: List<StatusEffect>): String =
        AbilityTextRenderer.renderBuild(build, statusEffects)

    open suspend fun decodeBuildBundle(text: String): DecodedSingle<BuildImportPreview> =
        when (val r = buildShareDecoder.decode(text)) {
            is BuildShareDecoder.Result.Loaded -> DecodedSingle.Loaded(r.preview)
            is BuildShareDecoder.Result.Failed -> DecodedSingle.Failed(r.reason)
        }
}
