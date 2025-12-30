package com.mobile.wizardry.compendium.model.core

sealed interface UiResult<out T> {

    object Loading : UiResult<Nothing>

    class Error(val e: Throwable) : UiResult<Nothing>

    class Success<T>(val data: T) : UiResult<T>
}
