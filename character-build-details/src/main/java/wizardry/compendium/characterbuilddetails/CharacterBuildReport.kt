package wizardry.compendium.characterbuilddetails

import wizardry.compendium.essences.model.AbsorbedEssence
import wizardry.compendium.essences.model.CharacterBuild
import kotlin.math.roundToInt

internal fun CharacterBuild.report(): String = buildString {
    appendLine(name)
    appendLine(race)
    appendLine()
    val pct = (progression * 100).roundToInt()
    appendLine("Rank: $rank     Progression: $pct%")
    appendLine()

    appendSlot("Power", Power.essence)
    appendSlot("Speed", Speed.essence)
    appendSlot("Spirit", Spirit.essence)
    appendSlot("Recovery", Recovery.essence)

    if (racialAbilities.isEmpty()) {
        append("Racial Abilities: (none)")
    } else {
        appendLine("Racial Abilities")
        racialAbilities.forEachIndexed { index, listing ->
            if (index == racialAbilities.lastIndex) append("  ${listing.name}")
            else appendLine("  ${listing.name}")
        }
    }
}

private fun StringBuilder.appendSlot(label: String, essence: AbsorbedEssence?) {
    appendLine(label)
    if (essence == null) {
        appendLine("  Essence: (none)")
    } else {
        appendLine("  Essence: ${essence.essence.name}")
        essence.abilities.forEach { acquired ->
            appendLine("    ${acquired.name} (${acquired.rank})")
        }
    }
    appendLine()
}
