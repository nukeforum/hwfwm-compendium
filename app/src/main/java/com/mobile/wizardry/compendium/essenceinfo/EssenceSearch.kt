package com.mobile.wizardry.compendium.essenceinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mobile.wizardry.compendium.EssenceProvider
import com.mobile.wizardry.compendium.LocalNavController
import com.mobile.wizardry.compendium.Nav
import com.mobile.wizardry.compendium.R
import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.essences.model.Rarity

private val skyBlue = Color(0xFF87CEEB)

@Composable
fun EssenceSearch(essenceProvider: EssenceProvider) {
    val navController = LocalNavController.current
    val essences by produceState(
        initialValue = listOf<Essence>(),
        producer = { value = essenceProvider.getEssences().sortedBy { it.name } }
    )
    var filter by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
        //                            .background(skyBlue)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(essences.filter { it.matchesFilter(filter) }, { it.hashCode() }) { essence ->
                EssenceListItem(
                    essence = essence,
                    modifier = Modifier
                        .clickable {
                            navController.navigate(Nav.EssenceDetail(essence).route)
                        }
                )
            }
        }
        TextField(
            label = { Text(text = "Type an essence name") },
            value = filter,
            onValueChange = { filter = it },
            modifier = Modifier
                .fillMaxWidth(),
            trailingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_x),
                    contentDescription = stringResource(R.string.clear_search_accessibility),
                    modifier = Modifier.clickable { filter = "" }
                )
            }
        )
    }
}

private fun Essence.matchesFilter(filter: String): Boolean {
    return name.lowercase().contains(filter.lowercase())
}

@Composable
fun EssenceListItem(
    modifier: Modifier = Modifier,
    essence: Essence
) {
    Row(
        modifier = modifier
            .padding(vertical = 4.dp)
            .background(Color.DarkGray)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(essence.name)
        if (essence.confluences.isEmpty())
            Text(essence.rarity.toString())
        else
            Text("Confluence")
    }
}

@Preview
@Composable
fun EssenceListItemPreview() {
    val essence = Essence.of("sin", "Manifested essence of transgression", Rarity.Legendary)
    EssenceListItem(Modifier, essence)
}
