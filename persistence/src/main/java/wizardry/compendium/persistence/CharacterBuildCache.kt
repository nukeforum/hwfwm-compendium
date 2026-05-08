package wizardry.compendium.persistence

import wizardry.compendium.essences.model.CharacterBuild

interface CharacterBuildCache {
    var contents: List<CharacterBuild>
}
