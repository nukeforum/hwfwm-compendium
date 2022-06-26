package com.mobile.wizardry.compendium

import android.content.res.AssetManager
import com.mobile.wizardry.compendium.dataloader.FileStreamSource
import java.io.InputStream

class AssetFileStreamSource(private val assetManager: AssetManager) : FileStreamSource {
    override fun getInputStreamFor(filename: String): InputStream {
        return assetManager.open(filename)
    }
}
