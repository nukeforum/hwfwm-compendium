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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mobile.wizardry.compendium.essenceinfo.EssenceDetails
import com.mobile.wizardry.compendium.essences.EssenceProvider
import com.mobile.wizardry.compendium.randomizer.Randomizer
import com.mobile.wizardry.compendium.search.EssenceSearch
import com.mobile.wizardry.compendium.ui.theme.CompendiumTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var essenceProvider: EssenceProvider

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
                                    ReturnToSearchButton(navController)
                                }
                            },
                            actions = {
                                if (currentRoute != Nav.EssenceRandomizer.route) RandomizerButton(navController)
                                CreateBuildButton(navController)
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
                                onEssenceClick = { essence ->
                                    navController.navigate(Nav.EssenceDetail.buildRoute(essence))
                                }
                            )
                        }
                        composable(Nav.EssenceRandomizer.route) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            Randomizer()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RandomizerButton(navController: NavHostController) {
    IconButton(
        onClick = {
            navController.navigate(Nav.EssenceRandomizer.route)
        }
    ) {
        Icon(Icons.Filled.Star, contentDescription = null)
    }
}

@Composable
private fun CreateBuildButton(navController: NavHostController) {
    IconButton(onClick = { /*TODO*/ }) {
        Icon(Icons.Filled.Build, contentDescription = null)
    }
}

@Composable
private fun ReturnToSearchButton(navController: NavHostController) {
    IconButton(
        onClick = {
            navController.popBackStack(
                route = Nav.EssenceSearch.route,
                inclusive = false,
            )
        }
    ) { Icon(Icons.Filled.Search, contentDescription = null) }
}
