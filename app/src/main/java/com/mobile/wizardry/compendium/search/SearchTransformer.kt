package com.mobile.wizardry.compendium.search

import com.mobile.wizardry.compendium.essences.model.Essence

sealed interface SearchTransformer {
    val name: String get() = this::class.java.simpleName
    fun predicate(essence: Essence): Boolean

    class Filter(val term: String) : SearchTransformer {
        override fun predicate(essence: Essence): Boolean {
            return essence.name.lowercase().contains(term.lowercase())
        }
    }

    object HideConfluences : SearchTransformer {
        override fun predicate(essence: Essence): Boolean {
            return essence !is Essence.Confluence
        }
    }
}

fun Collection<SearchTransformer>.getFilterTerm(): String {
    return find { it is SearchTransformer.Filter }
        ?.let { it as SearchTransformer.Filter }
        ?.term
        ?: ""
}
