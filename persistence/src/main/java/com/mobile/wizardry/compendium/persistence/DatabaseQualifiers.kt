package com.mobile.wizardry.compendium.persistence

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Canonical

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Contributions
