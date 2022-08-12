package com.mobile.wizardry.compendium.essences.dataloader

import java.io.InputStream

interface FileStreamSource {
    fun getInputStreamFor(filename: String): InputStream
}
