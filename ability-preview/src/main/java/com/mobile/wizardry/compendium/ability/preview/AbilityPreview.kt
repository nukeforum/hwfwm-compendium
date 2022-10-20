package com.mobile.wizardry.compendium.ability.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mobile.wizardry.compendium.ability.Report
import com.mobile.wizardry.compendium.essences.model.*

@Composable
fun AbilityPreview(ability: Ability) {
    Column(
        modifier = Modifier
            .background(Color.White)
            .padding(16.dp)
            .fillMaxSize()
    ) {
        ability.Report()
    }
}


@Preview
@Composable
fun Preview() {
    val ability = pathOfShadows.acquire(darkEssence).rankUp()
    AbilityPreview(ability)
}

val darkEssence = Essence.Manifestation(
    "Dark",
    Rank.Unranked,
    Rarity.Uncommon,
    listOf(Property.Consumable, Property.Essence),
    emptyList(),
    "",
    false
)

val pathOfShadows = Ability.Listing(
    name = "Path of Shadows",
    effects = listOf(
        Effect.AbilityEffect(
            Rank.Iron,
            type = AbilityType.SpecialAbility,
            properties = listOf(Property.Dimension, Property.Teleport),
            cost = listOf(
                Cost.Upfront(Amount.Low, Resource.Mana)
            ),
            cooldown = 0,
            "Teleport using shadows as a portal. You must be able to see the destination shadow."
        ),
        Effect.AbilityEffect(
            Rank.Bronze,
            type = AbilityType.SpecialAbility,
            properties = listOf(Property.Dimension, Property.Teleport),
            cost = listOf(
                Cost.Upfront(Amount.Low, Resource.Mana)
            ),
            cooldown = 0,
            "You can sense nearby shadows and teleport to them without requiring line of sight."
        ),
        Effect.AbilityEffect(
            Rank.Bronze,
            type = AbilityType.SpecialAbility,
            properties = listOf(Property.Dimension, Property.Teleport),
            cost = listOf(Cost.Upfront(Amount.Moderate, Resource.Mana)),
            cooldown = 0,
            "By increasing the cost to moderate, small shadows can be enlarged to serve as viable portals at both ingress and egress points."
        ),
        Effect.AbilityEffect(
            Rank.Bronze,
            type = AbilityType.Conjuration,
            properties = listOf(Property.Teleport),
            cost = listOf(Cost.Upfront(Amount.VeryHigh, Resource.Mana)),
            cooldown = 600,
            "Alternatively, conjure a shadow gate between two locations on a regional scale. The distant gate must appear in" +
                    " a location you have previously visited. This effect is a conjuration with a very high mana cost and a 10-minute" +
                    " cooldown. The iron-rank effect can still be used while this ability is on cooldown."
        ),
        Effect.AbilityEffect(
            Rank.Silver,
            type = AbilityType.Conjuration,
            properties = emptyList(),
            cost = listOf(Cost.Upfront(Amount.VeryHigh, Resource.Mana)),
            cooldown = 3600,
            description = "Range of static portals increased to eight-hundred kilometres. Portals beyond the bronze-range of four-hundred " +
                    "kilometres increased the cooldown from ten minutes to an hour. The bronze-rank effect can still be used while this ability is " +
                    "on cooldown."
        )
    ),
)
val ability = pathOfShadows.acquire(
    Essence.Manifestation(
        "Dark",
        Rank.Unranked,
        Rarity.Uncommon,
        listOf(Property.Consumable, Property.Essence),
        emptyList(),
        "",
        false
    )
)
