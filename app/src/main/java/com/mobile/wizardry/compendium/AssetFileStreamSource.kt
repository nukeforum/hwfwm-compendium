package com.mobile.wizardry.compendium

import android.content.Context
import android.content.res.AssetManager
import com.mobile.wizardry.compendium.essences.dataloader.FileStreamSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject

class AssetFileStreamSource
@Inject constructor(
    @ApplicationContext context: Context
) : FileStreamSource {
    private val assetManager: AssetManager = context.assets
    override fun getInputStreamFor(filename: String): InputStream {
        return assetManager.open(filename)
    }
}
