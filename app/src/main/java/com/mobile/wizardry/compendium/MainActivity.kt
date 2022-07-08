package com.mobile.wizardry.compendium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mobile.wizardry.compendium.dataloader.EssenceCsvLoader
import com.mobile.wizardry.compendium.dataloader.EssenceDataLoader
import com.mobile.wizardry.compendium.essenceinfo.EssenceDetails
import com.mobile.wizardry.compendium.essenceinfo.EssenceSearch
import com.mobile.wizardry.compendium.ui.theme.CompendiumTheme

class MainActivity : ComponentActivity() {
    private val essenceProvider: EssenceProvider by lazy { EssenceFileCacheProvider(dataLoader) }
    private val dataLoader: EssenceDataLoader by lazy {
        EssenceCsvLoader(AssetFileStreamSource(assets))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompendiumTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    NavHost(
                        navController = LocalNavController.current,
                        startDestination = Nav.EssenceSearch.route
                    ) {
                        composable(Nav.EssenceSearch.route) { EssenceSearch(essenceProvider) }
                        composable(
                            Nav.EssenceDetail().route,
                            arguments = listOf(
                                navArgument("essenceHash") { type = NavType.IntType }
                            )
                        ) { backStackEntry ->
                            val essenceHash = backStackEntry.arguments!!.getInt("essenceHash")
                            EssenceDetails(essenceProvider, essenceHash = essenceHash)
                        }
                    }
                }
            }
        }
    }
}
