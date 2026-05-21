package wizardry.compendium.statuseffect.details

import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.domain.model.StatusType

object StatusEffectTextRenderer {

    fun renderAsText(effect: StatusEffect): String = buildString {
        appendLine(effect.name)
        appendLine(typeLabel(effect.type))
        if (effect.stackable) appendLine("Stackable")
        appendLine()
        append(effect.description)
        if (effect.properties.isNotEmpty()) {
            appendLine()
            appendLine()
            append("Properties: ${effect.properties.joinToString(", ")}")
        }
    }

    private fun typeLabel(type: StatusType): String = when (type) {
        StatusType.Affliction.Curse -> "Affliction · Curse"
        StatusType.Affliction.Disease -> "Affliction · Disease"
        StatusType.Affliction.Elemental -> "Affliction · Elemental"
        StatusType.Affliction.Holy -> "Affliction · Holy"
        StatusType.Affliction.Magic -> "Affliction · Magic"
        StatusType.Affliction.Poison -> "Affliction · Poison"
        StatusType.Affliction.Unholy -> "Affliction · Unholy"
        StatusType.Affliction.Wound -> "Affliction · Wound"
        StatusType.Affliction.UnTyped -> "Affliction · Untyped"
        StatusType.Boon.Holy -> "Boon · Holy"
        StatusType.Boon.Magic -> "Boon · Magic"
        StatusType.Boon.Unholy -> "Boon · Unholy"
        StatusType.Boon.UnTyped -> "Boon · Untyped"
    }
}
