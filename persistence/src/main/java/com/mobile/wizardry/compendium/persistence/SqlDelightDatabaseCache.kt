package com.mobile.wizardry.compendium.persistence

import com.mobile.wizardry.compendium.essences.model.*
import com.mobile.wizardry.compendium.persistence.adapters.AmountAdapter
import com.mobile.wizardry.compendium.persistence.adapters.CostAdapter
import com.mobile.wizardry.compendium.persistence.adapters.PropertiesAdapter
import com.mobile.wizardry.compendium.persistence.adapters.ResourceAdapter
import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.EnumColumnAdapter
import com.squareup.sqldelight.db.SqlDriver
import javax.inject.Inject

class SqlDelightDatabaseCache
@Inject constructor(driver: SqlDriver) : EssenceCache {
    private val db = CompendiumDb(
        driver = driver,
        confluenceAdapter = Confluence.Adapter(ConfluenceSetAdapter()),
        essenceEffectAdapter = EssenceEffect.Adapter(
            rankAdapter = EnumColumnAdapter(),
            propertiesAdapter = PropertiesAdapter(),
            costAdapter = CostAdapter(
                amountAdapter = AmountAdapter(),
                resourceAdapter = ResourceAdapter(),
            )
        ),
        manifestationAdapter = Manifestation.Adapter(
            rarityAdapter = EnumColumnAdapter(),
            rankAdapter = EnumColumnAdapter(),
            propertiesAdapter = PropertiesAdapter(),
        ),
    )

    override var contents: List<Essence>
        get() = db.essenceQueries.getManifestations(
            mapper = { name, is_restricted, rank, rarity, properties, description ->
                Essence.Manifestation(
                    name = name,
                    rank = rank,
                    rarity = rarity,
                    properties = properties,
                    effects = 
                )
            }
        )
        set(value) {}

    class ItemEffectsAdapter : ColumnAdapter<List<Effect.ItemEffect>, String> {
        override fun decode(databaseValue: String): List<Effect.ItemEffect> {
            TODO("Not yet implemented")
        }

        override fun encode(value: List<Effect.ItemEffect>): String {
            TODO("Not yet implemented")
        }
    }

    class ConfluenceSetAdapter : ColumnAdapter<Set<ConfluenceSet>, String> {
        override fun decode(databaseValue: String): Set<ConfluenceSet> {
            return databaseValue.split(SET_DELIMITER)
                .map { setValue ->
                    val (firstName, secondName, thirdName, restrictionValue) = setValue.split(ESSENCE_DELIMITER)
                    ConfluenceSet(
                        isRestricted = restrictionValue == RESTRICTED_VALUE
                    )
                }
                .toSet()
        }

        override fun encode(value: Set<ConfluenceSet>): String {
            return value.joinToString(SET_DELIMITER) { confluenceSet ->
                confluenceSet.set.joinToString(ESSENCE_DELIMITER) { it.name }
                    .let {
                        if (confluenceSet.isRestricted) "$it:$RESTRICTED_VALUE"
                        else "$it:$UNRESTRICTED_VALUE"
                    }
            }
        }

        companion object {
            private const val SET_DELIMITER = ","
            private const val ESSENCE_DELIMITER = ":"
            private const val RESTRICTED_VALUE = "restricted"
            private const val UNRESTRICTED_VALUE = "unrestricted"
        }
    }
}
