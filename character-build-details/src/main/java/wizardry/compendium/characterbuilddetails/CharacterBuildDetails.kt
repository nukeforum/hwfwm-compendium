package wizardry.compendium.characterbuilddetails

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.ability.preview.AbilityPreview
import wizardry.compendium.ability.preview.LocalStatusEffects
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AbilityType
import wizardry.compendium.essences.model.AbsorbedEssence
import wizardry.compendium.essences.model.Amount
import wizardry.compendium.essences.model.Attribute
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.essences.model.Cost
import wizardry.compendium.essences.model.Effect
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.essences.model.Resource
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

    when (val details = state) {
        is CharacterBuildDetailUiState.Error -> ErrorMessage(details.exception.message ?: "Unable to load build")
        CharacterBuildDetailUiState.Loading -> Loading()
        is CharacterBuildDetailUiState.Success -> {
            onBuildLoaded(details.build)
            Details(
                state = details,
                onEdit = { onEditContribution(details.build) },
                shareText = viewModel::shareText,
                encodeFile = viewModel::encodeFile,
            )
        }
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
    shareText: () -> String,
    encodeFile: () -> String,
) {
    val context = LocalContext.current
    val sanitized = state.build.name.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(encodeFile().toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) {
                // Silent failure for v1; matches Settings export behavior.
            }
        }
    }

    CompositionLocalProvider(LocalStatusEffects provides state.statusEffects) {
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
                OutlinedButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText())
                    }
                    context.startActivity(Intent.createChooser(intent, "Share build"))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(text = " Share", modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { createDocumentLauncher.launch("$sanitized.compendium") }) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Text(text = " Export", modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text(text = " Edit", modifier = Modifier.padding(start = 4.dp))
                }
            }

            BuildHeader(state.build)

            AttributeSection("Power", state.build.Power.essence)
            AttributeSection("Speed", state.build.Speed.essence)
            AttributeSection("Spirit", state.build.Spirit.essence)
            AttributeSection("Recovery", state.build.Recovery.essence)

            RacialAbilitiesSection(state.build.racialAbilities)
        }
    }
}

@Composable
private fun BuildHeader(build: CharacterBuild) {
    val pct = (build.progression * 100).roundToInt()
    Text(text = build.name, style = MaterialTheme.typography.headlineSmall)
    Text(text = build.race, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(4.dp))
    Text(text = "Rank: ${build.rank}     Progression: $pct%")
}

@Composable
private fun AttributeSection(label: String, essence: AbsorbedEssence?) {
    Spacer(Modifier.height(16.dp))
    Text(text = label, style = MaterialTheme.typography.titleMedium)
    if (essence == null) {
        Text(text = "Essence: (none)")
        return
    }
    Text(text = "Essence: ${essence.essence.name}")
    essence.abilities.forEach { acquired ->
        Spacer(Modifier.height(8.dp))
        AbilityCard(ability = acquired, rankCeiling = acquired.rank)
    }
}

@Composable
private fun RacialAbilitiesSection(abilities: List<Ability.Listing>) {
    Spacer(Modifier.height(16.dp))
    if (abilities.isEmpty()) {
        Text(text = "Racial Abilities: (none)", style = MaterialTheme.typography.titleMedium)
        return
    }
    Text(text = "Racial Abilities", style = MaterialTheme.typography.titleMedium)
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
            shareText = { "" },
            encodeFile = { "" },
        )
    }
}
