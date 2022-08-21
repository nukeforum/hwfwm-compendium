package com.mobile.wizardry.compendium.search

import com.mobile.wizardry.compendium.essences.model.Essence

sealed interface SearchFilter {
    val name: String get() = this::class.java.simpleName
    fun predicate(essence: Essence): Boolean

    object HideConfluences : SearchFilter {
        override fun predicate(essence: Essence): Boolean {
            return essence !is Essence.Confluence
        }
    }

    companion object {
        val options = listOf<SearchFilter>(
            HideConfluences
        )
    }
}
