package com.mobile.wizardry.compendium.persistence.adapters

import com.mobile.wizardry.compendium.essences.model.Amount
import com.squareup.sqldelight.ColumnAdapter

class AmountAdapter : ColumnAdapter<Amount, String> {
    override fun decode(databaseValue: String): Amount {
        return when (databaseValue) {
            Amount.None.toString() -> Amount.None
            Amount.Low.toString() -> Amount.Low
            Amount.Moderate.toString() -> Amount.Moderate
            Amount.High.toString() -> Amount.High
            Amount.VeryHigh.toString() -> Amount.VeryHigh
            Amount.Extreme.toString() -> Amount.Extreme
            else -> error("Unsupported Amount $databaseValue: update AmountAdapter")
        }
    }

    override fun encode(value: Amount): String {
        return value.toString()
    }
}
