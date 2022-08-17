package com.mobile.wizardry.compendium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mobile.wizardry.compendium.essenceinfo.EssenceDetails
import com.mobile.wizardry.compendium.essenceinfo.EssenceSearch
import com.mobile.wizardry.compendium.essences.EssenceProvider
import com.mobile.wizardry.compendium.essences.dataloader.EssenceCsvLoader
import com.mobile.wizardry.compendium.essences.dataloader.EssenceDataLoader
import com.mobile.wizardry.compendium.persistence.EssenceCache
import com.mobile.wizardry.compendium.randomizer.Randomizer
import com.mobile.wizardry.compendium.ui.theme.CompendiumTheme

class MainActivity : ComponentActivity() {
    private val essenceProvider: EssenceProvider by lazy { EssenceRepository(dataLoader, EssenceCache.get()) }
    private val dataLoader: EssenceDataLoader by lazy {
        EssenceCsvLoader(AssetFileStreamSource(assets))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isHome by remember { mutableStateOf(true) }
            var title by remember { mutableStateOf("Magic Society Compendium") }
            CompendiumTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = title) },
                            navigationIcon = {
                                if (isHome.not()) {
                                    ReturnToSearchButton()
                                }
                            },
                            actions = {
                                RandomizerButton()
                                CreateBuildButton()
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { padding ->
                    NavHost(
                        modifier = Modifier.padding(padding),
                        navController = LocalNavController.current,
                        startDestination = Nav.EssenceDetailSearch.route
                    ) {
                        composable(Nav.EssenceDetailSearch.route) {
                            isHome = true
                            title = "Essence Search"
                            val navController = LocalNavController.current
                            EssenceSearch(essenceProvider) { essence ->
                                navController.navigate(Nav.EssenceDetail(essence = essence).route)
                            }
                        }
                        composable(
                            Nav.EssenceDetail().route,
                            arguments = listOf(
                                navArgument("essenceHash") { type = NavType.IntType }
                            )
                        ) { backStackEntry ->
                            isHome = false
                            val essenceHash = backStackEntry.arguments!!.getInt("essenceHash")
                            LaunchedEffect(Unit) {
                                essenceProvider.getEssences()
                                    .find { essence -> essence.hashCode() == essenceHash }
                                    ?.also { title = it.name }
                            }
                            EssenceDetails(essenceProvider, essenceHash = essenceHash)
                        }
                        composable(Nav.EssenceRandomizer.route) {
                            isHome = false
                            Randomizer(essenceProvider)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RandomizerButton() {
    val navHostController = LocalNavController.current
    IconButton(
        onClick = {
            navHostController.navigate(Nav.EssenceRandomizer.route)
        }
    ) {
        Icon(Icons.Filled.Star, contentDescription = null)
    }
}

@Composable
private fun CreateBuildButton() {
    val navHostController = LocalNavController.current
    IconButton(onClick = { /*TODO*/ }) {
        Icon(Icons.Filled.Build, contentDescription = null)
    }
}

@Composable
private fun ReturnToSearchButton() {
    val navHostController = LocalNavController.current
    IconButton(
        onClick = {
            navHostController.popBackStack(
                route = Nav.EssenceDetailSearch.route,
                inclusive = false,
            )
        }
    ) { Icon(Icons.Filled.Search, contentDescription = null) }
}
