package com.mobile.wizardry.compendium.model.core

sealed interface UiResult<out T> {
    val data: T

    object Loading : UiResult<Nothing> {
        override val data get() = throw Exception("Loading contains no data")
    }

    class Error(val e: Throwable) : UiResult<Nothing> {
        override val data get() = throw e
    }

    class Success<T>(override val data: T) : UiResult<T>
}
