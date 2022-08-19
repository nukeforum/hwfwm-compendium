package com.mobile.wizardry.compendium.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobile.wizardry.compendium.R
import com.mobile.wizardry.compendium.UiResult
import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.essences.model.Rarity
import com.mobile.wizardry.compendium.ui.theme.essenceHighlight

private val skyBlue = Color(0xFF87CEEB)

@Composable
fun EssenceSearch(
    viewModel: SearchViewModel = hiltViewModel(),
    onEssenceClicked: (Essence) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
//        .background(skyBlue)
    ) {
        when (state) {
            is UiResult.Error -> TODO()
            UiResult.Loading -> Loading(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .weight(1f)
            )
            is UiResult.Success -> Screen(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                essences = state.data.essences,
                filter = state.data.filter,
                onEssenceClicked = onEssenceClicked,
                onFilterChanged = { viewModel.setFilter(it) }
            )
        }
    }
}

@Composable
private fun Screen(
    modifier: Modifier,
    essences: List<Essence>,
    filter: String,
    onEssenceClicked: (Essence) -> Unit,
    onFilterChanged: (String) -> Unit,
) {
    LazyColumn(modifier = modifier) {
        items(essences, { it.hashCode() }) { essence ->
            EssenceListItem(
                essence = essence,
                modifier = Modifier
                    .clickable(onClick = { onEssenceClicked(essence) })
            )
        }
    }

    TextField(
        label = { Text(text = "Type an essence name") },
        value = filter,
        onValueChange = { onFilterChanged(it) },
        modifier = Modifier
            .fillMaxWidth(),
        trailingIcon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_x),
                contentDescription = stringResource(R.string.clear_search_accessibility),
                modifier = Modifier.clickable { onFilterChanged("") }
            )
        }
    )
}

@Composable
private fun Loading(
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = "Loading",
    )
}

@Composable
fun EssenceListItem(
    modifier: Modifier = Modifier,
    essence: Essence
) {
    Row(
        modifier = modifier
            .padding(vertical = 4.dp)
            .background(essenceHighlight(isRestricted = essence.isRestricted))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(essence.name)
        if (essence is Essence.Manifestation)
            Text(essence.rarity.toString())
        else
            Text("Confluence")
    }
}

@Preview
@Composable
fun EssenceListItemPreview() {
    val essence = Essence.of(
        name = "sin",
        restricted = false,
        description = "Manifested essence of transgression",
        rarity = Rarity.Legendary
    )
    EssenceListItem(essence = essence)
}
