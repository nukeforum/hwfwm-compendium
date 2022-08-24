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
            var isHome by remember { mutableStateOf(true) }
            var title by remember { mutableStateOf("Magic Society Compendium") }
            CompendiumTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = title) },
                            navigationIcon = {
                                if (isHome.not()) {
                                    ReturnToSearchButton(navController)
                                }
                            },
                            actions = {
                                RandomizerButton(navController)
                                CreateBuildButton(navController)
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { padding ->
                    NavHost(
                        modifier = Modifier.padding(padding),
                        navController = navController,
                        startDestination = Nav.EssenceDetailSearch.route
                    ) {
                        composable(Nav.EssenceDetailSearch.route) {
                            isHome = true
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
                            isHome = false
                            val essenceHash = backStackEntry.arguments!!.getInt("essenceHash")
                            LaunchedEffect(Unit) {
                                essenceProvider.getEssences()
                                    .find { essence -> essence.hashCode() == essenceHash }
                                    ?.also { title = it.name }
                            }
                            EssenceDetails(
                                essenceProvider = essenceProvider,
                                essenceHash = essenceHash,
                                onEssenceClick = { essence ->
                                    navController.navigate(Nav.EssenceDetail.buildRoute(essence))
                                }
                            )
                        }
                        composable(Nav.EssenceRandomizer.route) {
                            isHome = false
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
                route = Nav.EssenceDetailSearch.route,
                inclusive = false,
            )
        }
    ) { Icon(Icons.Filled.Search, contentDescription = null) }
}
