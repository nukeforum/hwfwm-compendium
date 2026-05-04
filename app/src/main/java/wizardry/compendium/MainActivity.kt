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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import wizardry.compendium.conflicts.ConflictsScreen
import wizardry.compendium.conflicts.ConflictsViewModel
import wizardry.compendium.share.ShareViewModel
import android.content.Intent as AndroidIntent
import android.content.Context
import wizardry.compendium.awakeningstone.search.AwakeningStoneSearch
import wizardry.compendium.awakeningstoneinfo.AwakeningStoneDetails
import wizardry.compendium.essence.contributions.EssenceContributionsScreen
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
            val shareViewModel = hiltViewModel<ShareViewModel>()
            val activityContext: Context = this
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
                                ConflictsBadge(navigate = { navController.navigate(Nav.Conflicts.route) })
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
                                onShareContribution = { essence ->
                                    fireShareIntent(activityContext, shareViewModel.encode(essence))
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
                                onShareContribution = { stone ->
                                    fireShareIntent(activityContext, shareViewModel.encode(stone))
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
                        composable(Nav.Conflicts.route) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            title = "Resolve Conflicts"
                            ConflictsScreen(
                                onEditEssenceContribution = { name ->
                                    navController.navigate("contributions?name=${android.net.Uri.encode(name)}")
                                },
                                onEditAwakeningStoneContribution = { name ->
                                    navController.navigate("stoneContributions?name=${android.net.Uri.encode(name)}")
                                },
                                onEditAbilityListingContribution = { name ->
                                    navController.navigate("abilityListingContributions?name=${android.net.Uri.encode(name)}")
                                },
                            )
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
                            EssenceContributionsScreen(
                                onContributionSaved = { navController.popBackStack() },
                                onContributionDeleted = { navController.popBackStack(Nav.EssenceSearch.route, false) },
                                onPasteImport = { text ->
                                    when (val result = shareViewModel.decodeSingleManifestation(text)) {
                                        is ShareViewModel.DecodedSingle.Loaded -> result.model to null
                                        is ShareViewModel.DecodedSingle.Failed -> null to result.reason
                                    }
                                },
                                onPasteImportConfluence = { text ->
                                    when (val result = shareViewModel.decodeConfluenceBundle(text)) {
                                        is ShareViewModel.DecodedSingle.Loaded -> result.model to null
                                        is ShareViewModel.DecodedSingle.Failed -> null to result.reason
                                    }
                                },
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
                                onContributionSaved = { navController.popBackStack() },
                                onContributionDeleted = { navController.popBackStack(Nav.AwakeningStoneSearch.route, false) },
                                onPasteImport = { text ->
                                    when (val result = shareViewModel.decodeSingleStone(text)) {
                                        is ShareViewModel.DecodedSingle.Loaded -> result.model to null
                                        is ShareViewModel.DecodedSingle.Failed -> null to result.reason
                                    }
                                },
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
                                onShareContribution = { listing ->
                                    fireShareIntent(activityContext, shareViewModel.encode(listing))
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
                                onContributionSaved = { navController.popBackStack() },
                                onContributionDeleted = { navController.popBackStack(Nav.AbilityListingSearch.route, false) },
                                onPasteImport = { text ->
                                    when (val result = shareViewModel.decodeSingleListing(text)) {
                                        is ShareViewModel.DecodedSingle.Loaded -> result.model to null
                                        is ShareViewModel.DecodedSingle.Failed -> null to result.reason
                                    }
                                },
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
private fun ConflictsBadge(
    navigate: () -> Unit,
    viewModel: ConflictsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    if (state.total == 0) return
    IconButton(onClick = navigate) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = "${state.total} contribution conflict(s)",
            tint = Color(0xFFD32F2F),
        )
    }
}

@Composable
private fun BackButton(navigate: () -> Unit) {
    IconButton(onClick = navigate) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

/**
 * Fires an Android `ACTION_SEND` chooser with the given share text.
 *
 * Single-entity shares always go through plain text — they're tiny enough
 * to fit comfortably in the share-size limit (typical encoded size is a
 * few hundred chars, well below the 100 KB cap).
 */
private fun fireShareIntent(context: Context, text: String) {
    val intent = AndroidIntent(AndroidIntent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(AndroidIntent.EXTRA_TEXT, text)
    }
    context.startActivity(AndroidIntent.createChooser(intent, "Share contribution"))
}
