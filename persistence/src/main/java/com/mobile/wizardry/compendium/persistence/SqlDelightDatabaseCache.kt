package com.mobile.wizardry.compendium.persistence

import com.mobile.wizardry.compendium.essences.model.Effect
import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.essences.model.Property
import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.EnumColumnAdapter
import com.squareup.sqldelight.db.SqlDriver
import javax.inject.Inject

class SqlDelightDatabaseCache
@Inject constructor(driver: SqlDriver) {
    private val db = CompendiumDb(
        driver,
        Confluence.Adapter(ConfluenceSetAdapter()),
        ,
        Manifestation.Adapter(
            rarityAdapter = EnumColumnAdapter(),
            rankAdapter = EnumColumnAdapter(),
            propertiesAdapter = PropertiesAdapter(),
            effectsAdapter = ItemEffectsAdapter(),
        ),
    )

    class ItemEffectsAdapter : ColumnAdapter<List<Effect.ItemEffect>, String> {
        override fun decode(databaseValue: String): List<Effect.ItemEffect> {
            TODO("Not yet implemented")
        }

        override fun encode(value: List<Effect.ItemEffect>): String {
            TODO("Not yet implemented")
        }
    }

    class PropertiesAdapter : ColumnAdapter<List<Property>, String> {
        override fun decode(databaseValue: String): List<Property> {
            return databaseValue.split(',')
                .map {
                    when (it) {
                        Property.Consumable.toString() -> Property.Consumable
                        Property.Essence.toString() -> Property.Essence
                        Property.Holy.toString() -> Property.Holy
                        Property.Unholy.toString() -> Property.Unholy
                        Property.Dimension.toString() -> Property.Dimension
                        Property.Nature.toString() -> Property.Nature
                        Property.Recovery.toString() -> Property.Recovery
                        Property.Cleanse.toString() -> Property.Cleanse
                        Property.Drain.toString() -> Property.Drain
                        Property.Dark.toString() -> Property.Dark
                        Property.Melee.toString() -> Property.Melee
                        Property.Curse.toString() -> Property.Curse
                        Property.Blood.toString() -> Property.Blood
                        Property.Darkness.toString() -> Property.Darkness
                        Property.Light.toString() -> Property.Light
                        Property.Teleport.toString() -> Property.Teleport
                        else -> error("Unsupported Property $it: update PropertiesAdapter")
                    }
                }
        }

        override fun encode(value: List<Property>): String {
            return value.joinToString(",")
        }
    }

    class ConfluenceSetAdapter : ColumnAdapter<Set<Essence.Manifestation>, String> {
        override fun decode(databaseValue: String): Set<Essence.Manifestation> {
            TODO("Not yet implemented")
        }

        override fun encode(value: Set<Essence.Manifestation>): String {
            TODO("Not yet implemented")
        }
    }
}
