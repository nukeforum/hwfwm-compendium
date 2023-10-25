package com.mobile.wizardry.compendium.persistence.adapters

import com.mobile.wizardry.compendium.essences.model.Resource
import com.squareup.sqldelight.ColumnAdapter

class ResourceAdapter : ColumnAdapter<Resource, String> {
    override fun decode(databaseValue: String): Resource {
        return when (databaseValue) {
            Resource.Mana.toString() -> Resource.Mana
            Resource.Stamina.toString() -> Resource.Stamina
            Resource.Health.toString() -> Resource.Health
            else -> error("Unsupported Resource $databaseValue: update ResourceAdapter")
        }
    }

    override fun encode(value: Resource): String {
        return value.toString()
    }
}
