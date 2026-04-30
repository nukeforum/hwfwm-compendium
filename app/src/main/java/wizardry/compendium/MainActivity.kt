package wizardry.compendium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
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
import wizardry.compendium.abilitylisting.contributions.AbilityListingContributionsScreen
import wizardry.compendium.abilitylisting.search.AbilityListingSearch
import wizardry.compendium.abilitylistinginfo.AbilityListingDetails
import wizardry.compendium.awakeningstone.contributions.AwakeningStoneContributionsScreen
import wizardry.compendium.awakeningstone.search.AwakeningStoneSearch
import wizardry.compendium.awakeningstoneinfo.AwakeningStoneDetails
import wizardry.compendium.contributions.ContributionsScreen
import wizardry.compendium.essenceinfo.EssenceDetails
import wizardry.compendium.randomizer.Randomizer
import wizardry.compendium.search.EssenceSearch
import wizardry.compendium.settings.SettingsScreen
import wizardry.compendium.ui.theme.CompendiumTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
                                if (currentRoute != Nav.Landing.route) {
                                    BackButton { navController.popBackStack() }
                                }
                            },
                            actions = {
                                if (currentRoute == Nav.EssenceSearch.route) {
                                    RandomizerButton {
                                        navController.navigate(Nav.EssenceRandomizer.route)
                                    }
                                    ContributeButton {
                                        navController.navigate(Nav.Contributions.newRoute)
                                    }
                                }
                                if (currentRoute == Nav.AwakeningStoneSearch.route) {
                                    ContributeButton {
                                        navController.navigate(Nav.AwakeningStoneContributions.newRoute)
                                    }
                                }
                                if (currentRoute == Nav.AbilityListingSearch.route) {
                                    ContributeButton {
                                        navController.navigate(Nav.AbilityListingContributions.newRoute)
                                    }
                                }
                                SettingsButton { navController.navigate(Nav.Settings.route) }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { padding ->
                    NavHost(
                        modifier = Modifier.padding(padding),
                        navController = navController,
                        startDestination = Nav.Landing.route
                    ) {
                        composable(Nav.Landing.route) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            title = "Magic Society Compendium"
                            LandingScreen(
                                onEssenceClicked = { navController.navigate(Nav.EssenceSearch.route) },
                                onAwakeningStoneClicked = { navController.navigate(Nav.AwakeningStoneSearch.route) },
                                onAbilityListingClicked = { navController.navigate(Nav.AbilityListingSearch.route) },
                            )
                        }
                        composable(Nav.EssenceSearch.route) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            title = "Essence Search"
                            EssenceSearch(
                                onEssenceClicked = { essence ->
                                    navController.navigate(Nav.EssenceDetail.buildRoute(essence))
                                },
                            )
                        }
                        composable(Nav.AwakeningStoneSearch.route) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            title = "Awakening Stone Search"
                            AwakeningStoneSearch(
                                onStoneClicked = { stone ->
                                    navController.navigate(Nav.AwakeningStoneDetail.buildRoute(stone))
                                },
                            )
                        }
                        composable(
                            Nav.EssenceDetail.route,
                            arguments = listOf(
                                navArgument(Nav.EssenceDetail.ARG_NAME) { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            val essenceName = backStackEntry.arguments!!.getString(Nav.EssenceDetail.ARG_NAME)!!
                            title = essenceName
                            EssenceDetails(
                                essenceName = essenceName,
                                onEssenceLoaded = { title = it.name },
                                onEditContribution = { essence ->
                                    navController.navigate(Nav.Contributions.buildEditRoute(essence))
                                },
                            )
                        }
                        composable(
                            Nav.AwakeningStoneDetail.route,
                            arguments = listOf(
                                navArgument(Nav.AwakeningStoneDetail.ARG_NAME) { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            val stoneName = backStackEntry.arguments!!.getString(Nav.AwakeningStoneDetail.ARG_NAME)!!
                            title = stoneName
                            AwakeningStoneDetails(
                                stoneName = stoneName,
                                onStoneLoaded = { title = it.name },
                                onEditContribution = { stone ->
                                    navController.navigate(Nav.AwakeningStoneContributions.buildEditRoute(stone))
                                },
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
                        composable(
                            Nav.Contributions.route,
                            arguments = listOf(
                                navArgument(Nav.Contributions.ARG_NAME) {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            ),
                        ) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            val editName = backStackEntry.arguments?.getString(Nav.Contributions.ARG_NAME)
                            title = if (editName != null) "Edit Contribution" else "Add Contribution"
                            ContributionsScreen(
                                onContributionDeleted = { navController.popBackStack(Nav.EssenceSearch.route, false) },
                            )
                        }
                        composable(
                            Nav.AwakeningStoneContributions.route,
                            arguments = listOf(
                                navArgument(Nav.AwakeningStoneContributions.ARG_NAME) {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            ),
                        ) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            val editName = backStackEntry.arguments?.getString(Nav.AwakeningStoneContributions.ARG_NAME)
                            title = if (editName != null) "Edit Awakening Stone" else "Add Awakening Stone"
                            AwakeningStoneContributionsScreen(
                                onContributionDeleted = { navController.popBackStack(Nav.AwakeningStoneSearch.route, false) },
                            )
                        }
                        composable(Nav.AbilityListingSearch.route) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            title = "Ability Listing Search"
                            AbilityListingSearch(
                                onListingClicked = { listing ->
                                    navController.navigate(Nav.AbilityListingDetail.buildRoute(listing))
                                },
                            )
                        }
                        composable(
                            Nav.AbilityListingDetail.route,
                            arguments = listOf(
                                navArgument(Nav.AbilityListingDetail.ARG_NAME) { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            val listingName = backStackEntry.arguments!!.getString(Nav.AbilityListingDetail.ARG_NAME)!!
                            title = listingName
                            AbilityListingDetails(
                                listingName = listingName,
                                onListingLoaded = { title = it.name },
                                onEditContribution = { listing ->
                                    navController.navigate(Nav.AbilityListingContributions.buildEditRoute(listing))
                                },
                            )
                        }
                        composable(
                            Nav.AbilityListingContributions.route,
                            arguments = listOf(
                                navArgument(Nav.AbilityListingContributions.ARG_NAME) {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            ),
                        ) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            val editName = backStackEntry.arguments?.getString(Nav.AbilityListingContributions.ARG_NAME)
                            title = if (editName != null) "Edit Ability Listing" else "Add Ability Listing"
                            AbilityListingContributionsScreen(
                                onContributionDeleted = { navController.popBackStack(Nav.AbilityListingSearch.route, false) },
                            )
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
private fun ContributeButton(navigate: () -> Unit) {
    IconButton(onClick = navigate) {
        Icon(Icons.Filled.Build, contentDescription = "Contribute")
    }
}

@Composable
private fun SettingsButton(navigate: () -> Unit) {
    IconButton(onClick = navigate) {
        Icon(Icons.Filled.Settings, contentDescription = "Settings")
    }
}

@Composable
private fun BackButton(navigate: () -> Unit) {
    IconButton(onClick = navigate) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}
