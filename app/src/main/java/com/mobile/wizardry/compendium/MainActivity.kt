package com.mobile.wizardry.compendium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mobile.wizardry.compendium.contributions.ContributionsScreen
import com.mobile.wizardry.compendium.essenceinfo.EssenceDetails
import com.mobile.wizardry.compendium.essences.EssenceProvider
import com.mobile.wizardry.compendium.randomizer.Randomizer
import com.mobile.wizardry.compendium.search.EssenceSearch
import com.mobile.wizardry.compendium.settings.SettingsScreen
import com.mobile.wizardry.compendium.ui.theme.CompendiumTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var essenceProvider: EssenceProvider

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            var currentRoute by remember { mutableStateOf<String?>(null) }
            var title by remember { mutableStateOf("Magic Society Compendium") }
            CompendiumTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = title) },
                            navigationIcon = {
                                if (currentRoute != Nav.EssenceSearch.route) {
                                    ReturnToSearchButton {
                                        navController.popBackStack(
                                            route = Nav.EssenceSearch.route,
                                            inclusive = false,
                                        )
                                    }
                                }
                            },
                            actions = {
                                if (currentRoute != Nav.EssenceRandomizer.route) {
                                    RandomizerButton { navController.navigate(Nav.EssenceRandomizer.route) }
                                }
                                ContributionsButton { navController.navigate(Nav.Contributions.route) }
                                SettingsButton { navController.navigate(Nav.Settings.route) }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { padding ->
                    NavHost(
                        modifier = Modifier.padding(padding),
                        navController = navController,
                        startDestination = Nav.EssenceSearch.route
                    ) {
                        composable(Nav.EssenceSearch.route) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            title = "Essence Search"
                            EssenceSearch(
                                onEssenceClicked = { essence ->
                                    navController.navigate(Nav.EssenceDetail.buildRoute(essence))
                                }
                            )
                        }
                        composable(
                            Nav.EssenceDetail.route,
                            arguments = listOf(
                                navArgument("essenceHash") { type = NavType.IntType }
                            )
                        ) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            val essenceHash = backStackEntry.arguments!!.getInt("essenceHash")
                            LaunchedEffect(Unit) {
                                essenceProvider.getEssences()
                                    .find { essence -> essence.hashCode() == essenceHash }
                                    ?.also { title = it.name }
                            }
                            EssenceDetails(
                                essenceHash = essenceHash,
                                onEssenceLoaded = { title = it.name }
                            )
                        }
                        composable(Nav.EssenceRandomizer.route) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            title = "Randomizer"
                            Randomizer()
                        }
                        composable(Nav.Settings.route) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            title = "Settings"
                            SettingsScreen()
                        }
                        composable(Nav.Contributions.route) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            title = "Add Contribution"
                            ContributionsScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RandomizerButton(navigate: () -> Unit) {
    IconButton(onClick = navigate) {
        Icon(Icons.Filled.Star, contentDescription = "Randomizer")
    }
}

@Composable
private fun ContributionsButton(navigate: () -> Unit) {
    IconButton(onClick = navigate) {
        Icon(Icons.Filled.Build, contentDescription = "Add contribution")
    }
}

@Composable
private fun SettingsButton(navigate: () -> Unit) {
    IconButton(onClick = navigate) {
        Icon(Icons.Filled.Settings, contentDescription = "Settings")
    }
}

@Composable
private fun ReturnToSearchButton(navigate: () -> Unit) {
    IconButton(onClick = navigate) {
        Icon(Icons.Filled.Search, contentDescription = null)
    }
}
