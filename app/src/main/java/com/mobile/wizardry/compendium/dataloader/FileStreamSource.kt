package com.mobile.wizardry.compendium.dataloader

import java.io.InputStream

interface FileStreamSource {
    fun getInputStreamFor(filename: String): InputStream
}
