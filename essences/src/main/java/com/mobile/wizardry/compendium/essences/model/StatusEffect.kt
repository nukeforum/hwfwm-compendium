package com.mobile.wizardry.compendium.essences.model

data class StatusEffect(
    val name: String,
    val type: StatusType,
    val stackable: Boolean,
    val effect: Effect,
)
