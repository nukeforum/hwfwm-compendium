package com.mobile.wizardry.compendium.data

interface Item {
    val name: String
    val rank: Rank
    val rarity: Rarity
    val properties: List<Property>
    val effects: List<Effect>
    val description: String
}
