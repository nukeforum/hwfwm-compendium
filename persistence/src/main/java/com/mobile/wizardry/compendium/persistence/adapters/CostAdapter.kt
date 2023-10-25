package com.mobile.wizardry.compendium.persistence.adapters

import com.mobile.wizardry.compendium.essences.model.Cost
import com.squareup.sqldelight.ColumnAdapter

class CostAdapter(
    private val amountAdapter: AmountAdapter,
    private val resourceAdapter: ResourceAdapter,
) : ColumnAdapter<List<Cost>, String> {
    override fun decode(databaseValue: String): List<Cost> {
        return databaseValue.split(COST_DELIMITER)
            .map { costValue ->
                val (typeValue, amountValue, resourceValue) = costValue.split(ELEMENT_DELIMITER)
                val amount = amountAdapter.decode(amountValue)
                val resource = resourceAdapter.decode(resourceValue)

                when(typeValue) {
                    Cost.Upfront::class.java.simpleName -> Cost.Upfront(amount, resource)
                    Cost.Ongoing::class.java.simpleName -> Cost.Ongoing(amount, resource)
                    else -> error("Unsupported Cost $databaseValue: update CostAdapter")
                }
            }
    }

    override fun encode(value: List<Cost>): String {
        return value.joinToString(",") {
            "${it.javaClass.simpleName}${amountAdapter.encode(it.amount)}:${resourceAdapter.encode(it.resource)}"
        }
    }

    companion object {
        private const val COST_DELIMITER = ","
        private const val ELEMENT_DELIMITER = ":"
    }
}
