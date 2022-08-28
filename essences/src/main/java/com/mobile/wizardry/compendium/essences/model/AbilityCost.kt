package com.mobile.wizardry.compendium.essences.model

data class AbilityCost(
    val resource: Resource,
    val amount: Amount,
    val ongoing: Boolean,
) {
    override fun toString(): String = "$amount $resource"
}
