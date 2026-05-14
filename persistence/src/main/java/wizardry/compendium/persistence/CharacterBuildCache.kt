package wizardry.compendium.persistence

import wizardry.compendium.domain.model.CharacterBuild

interface CharacterBuildCache {
    var contents: List<CharacterBuild>
}
