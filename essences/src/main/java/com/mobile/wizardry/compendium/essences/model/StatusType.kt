package com.mobile.wizardry.compendium.essences.model

sealed interface StatusType {
    object Boon: StatusType
    object Affliction: StatusType
    object Poison: StatusType
    object Curse: StatusType
    object Wound: StatusType
}
