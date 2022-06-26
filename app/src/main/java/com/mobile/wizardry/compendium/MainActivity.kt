package com.mobile.wizardry.compendium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mobile.wizardry.compendium.essenceinfo.EssenceDetails
import com.mobile.wizardry.compendium.essenceinfo.EssenceSearch
import com.mobile.wizardry.compendium.ui.theme.CompendiumTheme

class MainActivity : ComponentActivity() {
    private val essenceProvider: EssenceProvider = ManualEssenceProvider()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompendiumTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CompositionLocalProvider(
                        LocalNavController provides rememberNavController()
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
}
