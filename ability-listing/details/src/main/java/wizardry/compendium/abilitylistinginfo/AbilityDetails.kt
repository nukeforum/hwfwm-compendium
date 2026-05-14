package wizardry.compendium.abilitylistinginfo

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.ability.preview.AbilityPreview
import wizardry.compendium.ability.preview.LocalStatusEffects
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AbilityType
import wizardry.compendium.domain.model.Amount
import wizardry.compendium.domain.model.Cost
import wizardry.compendium.domain.model.Effect
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Resource
import wizardry.compendium.ui.PreviewLightDark
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.preferences.ThemeMode
import kotlin.time.Duration

@Composable
fun AbilityDetails(
    abilityName: String,
    onAbilityLoaded: (Ability.Listing) -> Unit,
    onEditContribution: (Ability.Listing) -> Unit = {},
    viewModel: AbilityListingDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(abilityName) {
        viewModel.load(abilityName)
    }

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.shareEvents.collect { event ->
            when (event) {
                is AbilityListingDetailViewModel.ShareEvent.Encoded -> fireShareIntent(context, event.text)
            }
        }
    }

    val state by viewModel.state.collectAsState()

    when (val details = state) {
        is AbilityListingDetailUiState.Error -> ErrorMessage(details.exception.message ?: "Unable to load ability")
        AbilityListingDetailUiState.Loading -> Loading()
        is AbilityListingDetailUiState.Success -> {
            onAbilityLoaded(details.listing)
            Details(
                state = details,
                onEdit = { onEditContribution(details.listing) },
                onShare = { viewModel.requestShare(details.listing) },
                onSelectRank = viewModel::selectRank,
            )
        }
    }
}

private fun fireShareIntent(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

@Composable
private fun ErrorMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message)
    }
}

@Composable
private fun Loading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Loading")
    }
}

@Composable
private fun Details(
    state: AbilityListingDetailUiState.Success,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onSelectRank: (Rank?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        if (state.isContribution) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(text = " Share", modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text(text = " Edit", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = Dp.Infinity, minHeight = 80.dp)
                .border(1.dp, Color.DarkGray)
                .padding(8.dp),
        ) {
            CompositionLocalProvider(LocalStatusEffects provides state.statusEffects) {
                AbilityPreview(
                    ability = state.listing,
                    rankCeiling = state.selectedRank,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        RankFilterRow(
            effects = state.listing.effects,
            selectedRank = state.selectedRank,
            onSelect = onSelectRank,
        )
    }
}

private fun sampleListing(): Ability.Listing = Ability.Listing(
    name = "Flame Bolt",
    effects = listOf(
        Effect.AbilityEffect(
            rank = Rank.Iron,
            type = AbilityType.Conjuration,
            properties = listOf(Property.Fire, Property.Magic),
            cost = listOf(Cost.Upfront(Amount.Moderate, Resource.Mana)),
            cooldown = Duration.ZERO,
            description = "Hurls a bolt of flame at a target.",
        ),
        Effect.AbilityEffect(
            rank = Rank.Bronze,
            type = AbilityType.Conjuration,
            properties = listOf(Property.Fire, Property.Magic),
            cost = listOf(Cost.Upfront(Amount.Moderate, Resource.Mana)),
            cooldown = Duration.ZERO,
            description = "Bolt fragments on impact, dealing splash damage.",
        ),
    ),
)

@PreviewLightDark
@Composable
private fun DetailsContributionPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Details(
            state = AbilityListingDetailUiState.Success(
                listing = sampleListing(),
                isContribution = true,
                statusEffects = emptyList(),
                selectedRank = null,
            ),
            onEdit = {},
            onShare = {},
            onSelectRank = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DetailsCanonicalPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Details(
            state = AbilityListingDetailUiState.Success(
                listing = sampleListing(),
                isContribution = false,
                statusEffects = emptyList(),
                selectedRank = Rank.Iron,
            ),
            onEdit = {},
            onShare = {},
            onSelectRank = {},
        )
    }
}
