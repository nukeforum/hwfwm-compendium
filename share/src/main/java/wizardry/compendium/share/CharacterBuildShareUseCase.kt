package wizardry.compendium.share

import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.wire.repo.WireIoRepository
import wizardry.compendium.wire.share.BuildImportPreview
import wizardry.compendium.wire.share.BuildShareDecoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encode and decode a single contributed [CharacterBuild] for the wire-format
 * export / paste-import flows. Plaintext rendering lives in the build's detail
 * UI module (see `AbilityTextRenderer.renderBuild`).
 */
@Singleton
open class CharacterBuildShareUseCase @Inject constructor(
    private val wireIo: WireIoRepository,
    private val buildShareDecoder: BuildShareDecoder,
) {
    open fun encode(build: CharacterBuild): String = wireIo.encodeSingle(build)

    open suspend fun decodeBuildBundle(text: String): DecodedSingle<BuildImportPreview> =
        when (val r = buildShareDecoder.decode(text)) {
            is BuildShareDecoder.Result.Loaded -> DecodedSingle.Loaded(r.preview)
            is BuildShareDecoder.Result.Failed -> DecodedSingle.Failed(r.reason)
        }
}
