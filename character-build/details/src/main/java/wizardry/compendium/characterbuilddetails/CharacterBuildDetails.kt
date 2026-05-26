package wizardry.compendium.characterbuilddetails

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.ability.preview.AbilityPreview
import wizardry.compendium.ability.preview.LocalStatusEffects
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AbilityType
import wizardry.compendium.domain.model.AbsorbedEssence
import wizardry.compendium.domain.model.Amount
import wizardry.compendium.domain.model.Attribute
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.Cost
import wizardry.compendium.domain.model.Effect
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.domain.model.Resource
import wizardry.compendium.ui.PreviewLightDark
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.preferences.ThemeMode
import kotlin.math.roundToInt
import kotlin.time.Duration

@Composable
fun CharacterBuildDetails(
    buildName: String,
    onBuildLoaded: (CharacterBuild) -> Unit,
    onEditContribution: (CharacterBuild) -> Unit = {},
    viewModel: CharacterBuildDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(buildName) { viewModel.load(buildName) }

    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.shareEvents.collect { event ->
            when (event) {
                is CharacterBuildDetailViewModel.ShareEvent.EncodedAsText ->
                    fireShareIntent(context, event.text, event.title)
                is CharacterBuildDetailViewModel.ShareEvent.Encoded -> {
                    val uri = pendingExportUri
                    if (uri != null) {
                        pendingExportUri = null
                        writeToUri(context, uri, event.text)
                    }
                }
            }
        }
    }

    when (val details = state) {
        is CharacterBuildDetailUiState.Error -> ErrorMessage(details.exception.message ?: "Unable to load build")
        CharacterBuildDetailUiState.Loading -> Loading()
        is CharacterBuildDetailUiState.Success -> {
            onBuildLoaded(details.build)
            Details(
                state = details,
                onEdit = { onEditContribution(details.build) },
                onShareText = { viewModel.requestShareAsText() },
                onExportFile = { uri ->
                    pendingExportUri = uri
                    viewModel.requestShareAsFile()
                },
            )
        }
    }
}

private fun fireShareIntent(context: Context, text: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_SUBJECT, title)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

private fun writeToUri(context: Context, uri: Uri, text: String) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        }
    } catch (_: Exception) {
        // Silent failure for v1; matches Settings export behavior.
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message) }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading") }
}

@Composable
private fun Details(
    state: CharacterBuildDetailUiState.Success,
    onEdit: () -> Unit,
    onShareText: () -> Unit,
    onExportFile: (Uri) -> Unit,
) {
    val sanitized = state.build.name.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        if (uri != null) onExportFile(uri)
    }

    CompositionLocalProvider(LocalStatusEffects provides state.statusEffects) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = { createDocumentLauncher.launch("$sanitized.compendium") }) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Text(text = " Export", modifier = Modifier.padding(start = 4.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onShareText) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Text(text = " Share", modifier = Modifier.padding(start = 4.dp))
                    }
                }

                BuildHeader(state.build)

                AttributeSection("Power", state.build.Power.essence)
                AttributeSection("Speed", state.build.Speed.essence)
                AttributeSection("Spirit", state.build.Spirit.essence)
                AttributeSection("Recovery", state.build.Recovery.essence)

                RacialAbilitiesSection(state.build.racialAbilities)

                Spacer(Modifier.height(88.dp))
            }

            FloatingActionButton(
                onClick = onEdit,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit build")
            }
        }
    }
}

@Composable
private fun BuildHeader(build: CharacterBuild) {
    val pct = (build.progression * 100).roundToInt()
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(text = build.name, style = MaterialTheme.typography.headlineSmall)
        Text(text = build.race, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(text = "Rank: ${build.rank}     Progression: $pct%")
    }
}

@Composable
private fun AttributeSection(label: String, essence: AbsorbedEssence?) {
    Spacer(Modifier.height(16.dp))
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Text(text = essence?.let { "Essence: ${it.essence.name}" } ?: "Essence: (none)")
    }
    if (essence == null) return
    essence.abilities.forEach { acquired ->
        Spacer(Modifier.height(8.dp))
        AbilityCard(ability = acquired, rankCeiling = acquired.rank)
    }
}

@Composable
private fun RacialAbilitiesSection(abilities: List<Ability.Listing>) {
    Spacer(Modifier.height(16.dp))
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = if (abilities.isEmpty()) "Racial Abilities: (none)" else "Racial Abilities",
            style = MaterialTheme.typography.titleMedium,
        )
    }
    if (abilities.isEmpty()) return
    abilities.forEach { listing ->
        Spacer(Modifier.height(8.dp))
        AbilityCard(ability = listing, rankCeiling = null)
    }
}

@Composable
private fun AbilityCard(ability: Ability, rankCeiling: Rank?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.DarkGray)
            .padding(8.dp),
    ) {
        AbilityPreview(ability = ability, rankCeiling = rankCeiling)
    }
}

private fun sampleListing(name: String = "Flame Bolt"): Ability.Listing = Ability.Listing(
    name = name,
    effects = listOf(
        Effect.AbilityEffect(
            rank = Rank.Iron,
            type = AbilityType.Conjuration,
            properties = listOf(Property.Fire, Property.Magic),
            cost = listOf(Cost.Upfront(Amount.Moderate, Resource.Mana)),
            cooldown = Duration.ZERO,
            description = "Hurls a bolt of flame at a target.",
        ),
    ),
)

private fun sampleManifestation(name: String = "Fire"): Essence.Manifestation = Essence.Manifestation(
    name = name,
    rank = Rank.Iron,
    rarity = Rarity.Common,
    properties = listOf(Property.Fire),
    description = "Manifested essence of fire",
    isRestricted = false,
)

private fun sampleBuild(): CharacterBuild = CharacterBuild(
    name = "Pyro Sentinel",
    race = "Human",
    racialAbilities = listOf(sampleListing("Adaptive Sight")),
    attributes = setOf(
        Attribute.Power(essence = AbsorbedEssence(essence = sampleManifestation("Fire"))),
        Attribute.Speed(),
        Attribute.Spirit(essence = AbsorbedEssence(essence = sampleManifestation("Wind"))),
        Attribute.Recovery(),
    ),
)

@PreviewLightDark
@Composable
private fun BuildHeaderPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        BuildHeader(build = sampleBuild())
    }
}

@PreviewLightDark
@Composable
private fun AttributeSectionFilledPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        AttributeSection(
            label = "Power",
            essence = AbsorbedEssence(essence = sampleManifestation("Fire")),
        )
    }
}

@PreviewLightDark
@Composable
private fun AttributeSectionEmptyPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        AttributeSection(label = "Speed", essence = null)
    }
}

@PreviewLightDark
@Composable
private fun RacialAbilitiesSectionPopulatedPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        RacialAbilitiesSection(abilities = listOf(sampleListing("Adaptive Sight"), sampleListing("Pack Tactics")))
    }
}

@PreviewLightDark
@Composable
private fun RacialAbilitiesSectionEmptyPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        RacialAbilitiesSection(abilities = emptyList())
    }
}

@PreviewLightDark
@Composable
private fun AbilityCardPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        AbilityCard(ability = sampleListing(), rankCeiling = Rank.Iron)
    }
}

@PreviewLightDark
@Composable
private fun DetailsPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Details(
            state = CharacterBuildDetailUiState.Success(
                build = sampleBuild(),
                statusEffects = emptyList(),
            ),
            onEdit = {},
            onShareText = {},
            onExportFile = {},
        )
    }
}
