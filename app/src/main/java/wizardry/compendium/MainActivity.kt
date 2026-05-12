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
import wizardry.compendium.about.AboutScreen
import wizardry.compendium.abilitylisting.contributions.AbilityContributionsScreen
import wizardry.compendium.abilitylisting.search.AbilitySearch
import wizardry.compendium.abilitylistinginfo.AbilityDetails
import wizardry.compendium.awakeningstone.contributions.AwakeningStoneContributionsScreen
import wizardry.compendium.conflicts.ConflictsScreen
import wizardry.compendium.conflicts.ConflictsViewModel
import wizardry.compendium.share.ShareViewModel
import android.content.Intent as AndroidIntent
import android.content.Context
import wizardry.compendium.awakeningstone.search.AwakeningStoneSearch
import wizardry.compendium.awakeningstoneinfo.AwakeningStoneDetails
import wizardry.compendium.characterbuild.contributions.CharacterBuildContributionsScreen
import wizardry.compendium.characterbuild.search.CharacterBuildSearch
import wizardry.compendium.characterbuilddetails.CharacterBuildDetails
import wizardry.compendium.essence.contributions.EssenceContributionsScreen
import wizardry.compendium.statuseffect.contributions.StatusEffectContributionsScreen
import wizardry.compendium.statuseffect.details.StatusEffectDetails
import wizardry.compendium.statuseffect.search.StatusEffectSearch
import wizardry.compendium.essenceinfo.EssenceDetails
import wizardry.compendium.randomizer.Randomizer
import wizardry.compendium.search.EssenceSearch
import wizardry.compendium.settings.SettingsScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import wizardry.compendium.theme.ThemeSettingsViewModel
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.ui.theme.ThemeMode
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
            val themeSettingsViewModel = hiltViewModel<ThemeSettingsViewModel>()
            val themeMode by themeSettingsViewModel.themeMode.collectAsState()
            val dynamicColor by themeSettingsViewModel.dynamicColorEnabled.collectAsState()
            CompendiumTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                val useDark = when (themeMode) {
                    ThemeMode.System -> isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        WindowCompat.setDecorFitsSystemWindows(window, false)
                        WindowInsetsControllerCompat(window, view).apply {
                            isAppearanceLightStatusBars = !useDark
                            isAppearanceLightNavigationBars = !useDark
                        }
                    }
                }
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
                                if (currentRoute == Nav.AbilitySearch.route) {
                                    ContributeButton {
                                        navController.navigate(Nav.AbilityContributions.newRoute)
                                    }
                                }
                                if (currentRoute == Nav.StatusEffectSearch.route) {
                                    ContributeButton {
                                        navController.navigate(Nav.StatusEffectContributions.newRoute)
                                    }
                                }
                                if (currentRoute == Nav.CharacterBuildSearch.route) {
                                    ContributeButton {
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
                        composable(Nav.Landing.route) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            title = "Magic Society Compendium"
                            LandingScreen(
                                onEssenceClicked = { navController.navigate(Nav.EssenceSearch.route) },
                                onAwakeningStoneClicked = { navController.navigate(Nav.AwakeningStoneSearch.route) },
                                onAbilitiesClicked = { navController.navigate(Nav.AbilitySearch.route) },
                                onStatusEffectClicked = { navController.navigate(Nav.StatusEffectSearch.route) },
                                onCharacterBuildClicked = { navController.navigate(Nav.CharacterBuildSearch.route) },
                            )
                        }
                        composable(Nav.EssenceSearch.route) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            title = "Essence Search"
                            EssenceSearch(
                                onEssenceClicked = { essence ->
                                    navController.navigate(Nav.EssenceDetail.buildRoute(essence))
                                },
                                onAddClicked = {
                                    navController.navigate(Nav.Contributions.newRoute)
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
                            SettingsScreen(
                                onAboutClick = { navController.navigate(Nav.About.route) },
                            )
                        }
                        composable(Nav.About.route) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            title = "About"
                            AboutScreen()
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
                                onEditAbilityContribution = { name ->
                                    navController.navigate("abilityListingContributions?name=${android.net.Uri.encode(name)}")
                                },
                                onEditStatusEffectContribution = { name ->
                                    navController.navigate("statusEffectContributions?name=${android.net.Uri.encode(name)}")
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
                            title = if (editName != null) "Edit Essence" else "Add Essence"
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
                        composable(Nav.AbilitySearch.route) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            title = "Ability Search"
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
                        ) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            val abilityName = backStackEntry.arguments!!.getString(Nav.AbilityDetail.ARG_NAME)!!
                            title = abilityName
                            AbilityDetails(
                                abilityName = abilityName,
                                onAbilityLoaded = { title = it.name },
                                onEditContribution = { ability ->
                                    navController.navigate(Nav.AbilityContributions.buildEditRoute(ability))
                                },
                                onShareContribution = { ability ->
                                    fireShareIntent(activityContext, shareViewModel.encode(ability))
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
                        ) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            val editName = backStackEntry.arguments?.getString(Nav.AbilityContributions.ARG_NAME)
                            title = if (editName != null) "Edit Ability" else "Add Ability"
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
                        composable(Nav.StatusEffectSearch.route) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            title = "Status Effect Search"
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
                        ) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            val effectName = backStackEntry.arguments!!.getString(Nav.StatusEffectDetail.ARG_NAME)!!
                            title = effectName
                            StatusEffectDetails(
                                effectName = effectName,
                                onEffectLoaded = { title = it.name },
                                onEditContribution = { effect ->
                                    navController.navigate(Nav.StatusEffectContributions.buildEditRoute(effect))
                                },
                                onShareContribution = { effect ->
                                    fireShareIntent(activityContext, shareViewModel.encode(effect))
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
                        ) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            val editName = backStackEntry.arguments?.getString(Nav.StatusEffectContributions.ARG_NAME)
                            title = if (editName != null) "Edit Status Effect" else "Add Status Effect"
                            StatusEffectContributionsScreen(
                                onContributionSaved = { navController.popBackStack() },
                                onContributionDeleted = { navController.popBackStack(Nav.StatusEffectSearch.route, false) },
                                onPasteImport = { text ->
                                    when (val result = shareViewModel.decodeSingleStatusEffect(text)) {
                                        is ShareViewModel.DecodedSingle.Loaded -> result.model to null
                                        is ShareViewModel.DecodedSingle.Failed -> null to result.reason
                                    }
                                },
                            )
                        }
                        composable(Nav.CharacterBuildSearch.route) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            title = "Character Build Search"
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
                        ) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            val buildName = backStackEntry.arguments!!.getString(Nav.CharacterBuildDetail.ARG_NAME)!!
                            title = buildName
                            CharacterBuildDetails(
                                buildName = buildName,
                                onBuildLoaded = { title = it.name },
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
                        ) { backStackEntry ->
                            currentRoute = backStackEntry.destination.route
                            val editName = backStackEntry.arguments?.getString(Nav.CharacterBuildContributions.ARG_NAME)
                            title = if (editName != null) "Edit Build" else "Add Build"
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
