package wizardry.compendium.domain.model

data class StatusEffect(
    val name: String,
    val type: StatusType,
    val properties: List<Property>,
    val stackable: Boolean,
    val description: String,
)
