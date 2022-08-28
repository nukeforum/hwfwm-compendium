package com.mobile.wizardry.compendium.essences.model

// Power ranking of items, abilities, and individuals
enum class Rank {
    Unranked,
    Iron,
    Bronze,
    Silver,
    Gold,
    Diamond,
    Transcendent,
    ;

    companion object {
        fun next(rank: Rank): Rank = when (rank) {
            Unranked -> Iron
            Iron -> Bronze
            Bronze -> Silver
            Silver -> Gold
            Gold -> Diamond
            Diamond -> Transcendent
            Transcendent -> Transcendent
        }
    }
}
