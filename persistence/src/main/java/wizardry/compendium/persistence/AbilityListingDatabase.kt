package wizardry.compendium.persistence

import app.cash.sqldelight.db.SqlDriver
import wizardry.compendium.essences.model.Ability
import javax.inject.Inject

class AbilityListingDatabase @Inject constructor(driver: SqlDriver) {
    private val db = CompendiumDatabase(driver)

    fun writeAll(listings: List<Ability.Listing>) {
        db.transaction {
            db.abilityListingsQueries.deleteAllAbilityListings()
            listings.forEach { listing ->
                db.abilityListingsQueries.insertAbilityListing(name = listing.name)
            }
        }
    }

    fun readAll(): List<Ability.Listing> {
        return db.abilityListingsQueries.selectAllAbilityListings().executeAsList()
            .map { name -> Ability.Listing.of(name = name) }
            .sortedBy { it.name }
    }
}
