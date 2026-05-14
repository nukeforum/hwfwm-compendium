package wizardry.compendium

import android.content.Context
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import wizardry.compendium.about.AboutScreen
import wizardry.compendium.abilitylisting.contributions.AbilityContributionsScreen
import wizardry.compendium.abilitylisting.search.AbilitySearch
import wizardry.compendium.abilitylistinginfo.AbilityDetails
import wizardry.compendium.awakeningstone.contributions.AwakeningStoneContributionsScreen
import wizardry.compendium.awakeningstone.search.AwakeningStoneSearch
import wizardry.compendium.awakeningstoneinfo.AwakeningStoneDetails
import wizardry.compendium.characterbuild.contributions.CharacterBuildContributionsScreen
import wizardry.compendium.characterbuild.search.CharacterBuildSearch
import wizardry.compendium.characterbuilddetails.CharacterBuildDetails
import wizardry.compendium.conflicts.ConflictsScreen
import wizardry.compendium.conflicts.ConflictsViewModel
import wizardry.compendium.essence.contributions.EssenceContributionsScreen
import wizardry.compendium.essenceinfo.EssenceDetails
import wizardry.compendium.randomizer.Randomizer
import wizardry.compendium.search.EssenceSearch
import wizardry.compendium.settings.SettingsScreen
import wizardry.compendium.share.ShareViewModel
import wizardry.compendium.statuseffect.contributions.StatusEffectContributionsScreen
import wizardry.compendium.statuseffect.details.StatusEffectDetails
import wizardry.compendium.statuseffect.search.StatusEffectSearch
import wizardry.compendium.theme.CompendiumThemeFromSettings
import android.content.Intent as AndroidIntent

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            val shareViewModel = hiltViewModel<ShareViewModel>()

            CompendiumThemeFromSettings {
                // Async-loaded entity title (set by detail screens via onXLoaded). Re-keyed per route.
                var loadedTitle by remember(currentRoute) { mutableStateOf<String?>(null) }
                val defaultTitle = titleForRoute(currentRoute, backStackEntry)
                val title = loadedTitle ?: defaultTitle

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = title) },
                            navigationIcon = {
                                if (currentRoute != null && currentRoute != Nav.Landing.route) {
                                    BackButton { navController.popBackStack() }
                                }
                            },
                            actions = {
                                when (currentRoute) {
                                    Nav.EssenceSearch.route -> {
                                        RandomizerButton {
                                            navController.navigate(Nav.EssenceRandomizer.route)
                                        }
                                        ContributeButton {
                                            navController.navigate(Nav.Contributions.newRoute)
                                        }
                                    }
                                    Nav.AwakeningStoneSearch.route -> ContributeButton {
                                        navController.navigate(Nav.AwakeningStoneContributions.newRoute)
                                    }
                                    Nav.AbilitySearch.route -> ContributeButton {
                                        navController.navigate(Nav.AbilityContributions.newRoute)
                                    }
                                    Nav.StatusEffectSearch.route -> ContributeButton {
                                        navController.navigate(Nav.StatusEffectContributions.newRoute)
                                    }
                                    Nav.CharacterBuildSearch.route -> ContributeButton {
                                        navController.navigate(Nav.CharacterBuildContributions.newRoute)
                                    }
                                }
                                ConflictsBadge(navigate = { navController.navigate(Nav.Conflicts.route) })
                                if (currentRoute != Nav.Settings.route) {
                                    SettingsButton { navController.navigate(Nav.Settings.route) }
                                }
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
                        composable(Nav.Landing.route) {
                            LandingScreen(
                                onEssenceClicked = { navController.navigate(Nav.EssenceSearch.route) },
                                onAwakeningStoneClicked = { navController.navigate(Nav.AwakeningStoneSearch.route) },
                                onAbilitiesClicked = { navController.navigate(Nav.AbilitySearch.route) },
                                onStatusEffectClicked = { navController.navigate(Nav.StatusEffectSearch.route) },
                                onCharacterBuildClicked = { navController.navigate(Nav.CharacterBuildSearch.route) },
                            )
                        }
                        composable(Nav.EssenceSearch.route) {
                            EssenceSearch(
                                onEssenceClicked = { essence ->
                                    navController.navigate(Nav.EssenceDetail.buildRoute(essence))
                                },
                                onAddClicked = {
                                    navController.navigate(Nav.Contributions.newRoute)
                                },
                            )
                        }
                        composable(Nav.AwakeningStoneSearch.route) {
                            AwakeningStoneSearch(
                                onStoneClicked = { stone ->
                                    navController.navigate(Nav.AwakeningStoneDetail.buildRoute(stone))
                                },
                                onAddClicked = {
                                    navController.navigate(Nav.AwakeningStoneContributions.newRoute)
                                },
                            )
                        }
                        composable(
                            Nav.EssenceDetail.route,
                            arguments = listOf(
                                navArgument(Nav.EssenceDetail.ARG_NAME) { type = NavType.StringType }
                            )
                        ) { entry ->
                            val essenceName = requireNotNull(entry.arguments?.getString(Nav.EssenceDetail.ARG_NAME)) {
                                "${Nav.EssenceDetail.ARG_NAME} required for ${Nav.EssenceDetail.route}"
                            }
                            val context = LocalContext.current
                            EssenceDetails(
                                essenceName = essenceName,
                                onEssenceLoaded = { loadedTitle = it.name },
                                onEditContribution = { essence ->
                                    navController.navigate(Nav.Contributions.buildEditRoute(essence))
                                },
                                onShareContribution = { essence ->
                                    fireShareIntent(context, shareViewModel.encode(essence))
                                },
                            )
                        }
                        composable(
                            Nav.AwakeningStoneDetail.route,
                            arguments = listOf(
                                navArgument(Nav.AwakeningStoneDetail.ARG_NAME) { type = NavType.StringType }
                            )
                        ) { entry ->
                            val stoneName = requireNotNull(entry.arguments?.getString(Nav.AwakeningStoneDetail.ARG_NAME)) {
                                "${Nav.AwakeningStoneDetail.ARG_NAME} required for ${Nav.AwakeningStoneDetail.route}"
                            }
                            AwakeningStoneDetails(
                                stoneName = stoneName,
                                onStoneLoaded = { loadedTitle = it.name },
                                onEditContribution = { stone ->
                                    navController.navigate(Nav.AwakeningStoneContributions.buildEditRoute(stone))
                                },
                            )
                        }
                        composable(Nav.EssenceRandomizer.route) {
                            Randomizer()
                        }
                        composable(Nav.Settings.route) {
                            SettingsScreen(
                                onAboutClick = { navController.navigate(Nav.About.route) },
                            )
                        }
                        composable(Nav.About.route) {
                            AboutScreen(versionName = BuildConfig.VERSION_NAME)
                        }
                        composable(Nav.Conflicts.route) {
                            ConflictsScreen(
                                onEditEssenceContribution = { name ->
                                    navController.navigate(Nav.Contributions.buildEditRouteByName(name))
                                },
                                onEditAwakeningStoneContribution = { name ->
                                    navController.navigate(Nav.AwakeningStoneContributions.buildEditRouteByName(name))
                                },
                                onEditAbilityContribution = { name ->
                                    navController.navigate(Nav.AbilityContributions.buildEditRouteByName(name))
                                },
                                onEditStatusEffectContribution = { name ->
                                    navController.navigate(Nav.StatusEffectContributions.buildEditRouteByName(name))
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
                        ) {
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
                        ) {
                            AwakeningStoneContributionsScreen(
                                onContributionSaved = { navController.popBackStack() },
                                onContributionDeleted = { navController.popBackStack(Nav.AwakeningStoneSearch.route, false) },
                            )
                        }
                        composable(Nav.AbilitySearch.route) {
                            AbilitySearch(
                                onAbilityClicked = { ability ->
                                    navController.navigate(Nav.AbilityDetail.buildRoute(ability))
                                },
                                onAddClicked = {
                                    navController.navigate(Nav.AbilityContributions.newRoute)
                                },
                            )
                        }
                        composable(
                            Nav.AbilityDetail.route,
                            arguments = listOf(
                                navArgument(Nav.AbilityDetail.ARG_NAME) { type = NavType.StringType }
                            )
                        ) { entry ->
                            val abilityName = requireNotNull(entry.arguments?.getString(Nav.AbilityDetail.ARG_NAME)) {
                                "${Nav.AbilityDetail.ARG_NAME} required for ${Nav.AbilityDetail.route}"
                            }
                            val context = LocalContext.current
                            AbilityDetails(
                                abilityName = abilityName,
                                onAbilityLoaded = { loadedTitle = it.name },
                                onEditContribution = { ability ->
                                    navController.navigate(Nav.AbilityContributions.buildEditRoute(ability))
                                },
                                onShareContribution = { ability ->
                                    fireShareIntent(context, shareViewModel.encode(ability))
                                },
                            )
                        }
                        composable(
                            Nav.AbilityContributions.route,
                            arguments = listOf(
                                navArgument(Nav.AbilityContributions.ARG_NAME) {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            ),
                        ) {
                            AbilityContributionsScreen(
                                onContributionSaved = { navController.popBackStack() },
                                onContributionDeleted = { navController.popBackStack(Nav.AbilitySearch.route, false) },
                                onPasteImport = { text ->
                                    when (val result = shareViewModel.decodeSingleAbility(text)) {
                                        is ShareViewModel.DecodedSingle.Loaded -> result.model to null
                                        is ShareViewModel.DecodedSingle.Failed -> null to result.reason
                                    }
                                },
                            )
                        }
                        composable(Nav.StatusEffectSearch.route) {
                            StatusEffectSearch(
                                onEffectClicked = { effect ->
                                    navController.navigate(Nav.StatusEffectDetail.buildRoute(effect))
                                },
                                onAddClicked = {
                                    navController.navigate(Nav.StatusEffectContributions.newRoute)
                                },
                            )
                        }
                        composable(
                            Nav.StatusEffectDetail.route,
                            arguments = listOf(
                                navArgument(Nav.StatusEffectDetail.ARG_NAME) { type = NavType.StringType }
                            )
                        ) { entry ->
                            val effectName = requireNotNull(entry.arguments?.getString(Nav.StatusEffectDetail.ARG_NAME)) {
                                "${Nav.StatusEffectDetail.ARG_NAME} required for ${Nav.StatusEffectDetail.route}"
                            }
                            StatusEffectDetails(
                                effectName = effectName,
                                onEffectLoaded = { loadedTitle = it.name },
                                onEditContribution = { effect ->
                                    navController.navigate(Nav.StatusEffectContributions.buildEditRoute(effect))
                                },
                            )
                        }
                        composable(
                            Nav.StatusEffectContributions.route,
                            arguments = listOf(
                                navArgument(Nav.StatusEffectContributions.ARG_NAME) {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            ),
                        ) {
                            StatusEffectContributionsScreen(
                                onContributionSaved = { navController.popBackStack() },
                                onContributionDeleted = { navController.popBackStack(Nav.StatusEffectSearch.route, false) },
                            )
                        }
                        composable(Nav.CharacterBuildSearch.route) {
                            CharacterBuildSearch(
                                onBuildClicked = { build ->
                                    navController.navigate(Nav.CharacterBuildDetail.buildRoute(build))
                                },
                                onAddClicked = {
                                    navController.navigate(Nav.CharacterBuildContributions.newRoute)
                                },
                            )
                        }
                        composable(
                            Nav.CharacterBuildDetail.route,
                            arguments = listOf(
                                navArgument(Nav.CharacterBuildDetail.ARG_NAME) { type = NavType.StringType }
                            ),
                        ) { entry ->
                            val buildName = requireNotNull(entry.arguments?.getString(Nav.CharacterBuildDetail.ARG_NAME)) {
                                "${Nav.CharacterBuildDetail.ARG_NAME} required for ${Nav.CharacterBuildDetail.route}"
                            }
                            CharacterBuildDetails(
                                buildName = buildName,
                                onBuildLoaded = { loadedTitle = it.name },
                                onEditContribution = { build ->
                                    navController.navigate(Nav.CharacterBuildContributions.buildEditRoute(build))
                                },
                            )
                        }
                        composable(
                            Nav.CharacterBuildContributions.route,
                            arguments = listOf(
                                navArgument(Nav.CharacterBuildContributions.ARG_NAME) {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            ),
                        ) {
                            CharacterBuildContributionsScreen(
                                onContributionSaved = { navController.popBackStack() },
                                onContributionDeleted = { navController.popBackStack(Nav.CharacterBuildSearch.route, false) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun titleForRoute(route: String?, entry: NavBackStackEntry?): String = when (route) {
    null, Nav.Landing.route -> "Magic Society Compendium"
    Nav.EssenceSearch.route -> "Essence Search"
    Nav.AwakeningStoneSearch.route -> "Awakening Stone Search"
    Nav.AbilitySearch.route -> "Ability Search"
    Nav.StatusEffectSearch.route -> "Status Effect Search"
    Nav.CharacterBuildSearch.route -> "Character Build Search"
    Nav.EssenceRandomizer.route -> "Randomizer"
    Nav.Settings.route -> "Settings"
    Nav.About.route -> "About"
    Nav.Conflicts.route -> "Resolve Conflicts"
    Nav.EssenceDetail.route -> entry?.arguments?.getString(Nav.EssenceDetail.ARG_NAME).orEmpty()
    Nav.AwakeningStoneDetail.route -> entry?.arguments?.getString(Nav.AwakeningStoneDetail.ARG_NAME).orEmpty()
    Nav.AbilityDetail.route -> entry?.arguments?.getString(Nav.AbilityDetail.ARG_NAME).orEmpty()
    Nav.StatusEffectDetail.route -> entry?.arguments?.getString(Nav.StatusEffectDetail.ARG_NAME).orEmpty()
    Nav.CharacterBuildDetail.route -> entry?.arguments?.getString(Nav.CharacterBuildDetail.ARG_NAME).orEmpty()
    Nav.Contributions.route ->
        if (entry?.arguments?.getString(Nav.Contributions.ARG_NAME) != null) "Edit Essence" else "Add Essence"
    Nav.AwakeningStoneContributions.route ->
        if (entry?.arguments?.getString(Nav.AwakeningStoneContributions.ARG_NAME) != null) "Edit Awakening Stone" else "Add Awakening Stone"
    Nav.AbilityContributions.route ->
        if (entry?.arguments?.getString(Nav.AbilityContributions.ARG_NAME) != null) "Edit Ability" else "Add Ability"
    Nav.StatusEffectContributions.route ->
        if (entry?.arguments?.getString(Nav.StatusEffectContributions.ARG_NAME) != null) "Edit Status Effect" else "Add Status Effect"
    Nav.CharacterBuildContributions.route ->
        if (entry?.arguments?.getString(Nav.CharacterBuildContributions.ARG_NAME) != null) "Edit Build" else "Add Build"
    else -> "Magic Society Compendium"
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
    val total = state.total
    if (total == 0) return
    val description = pluralStringResource(R.plurals.contribution_conflicts, total, total)
    IconButton(
        onClick = navigate,
        modifier = Modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.error,
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
