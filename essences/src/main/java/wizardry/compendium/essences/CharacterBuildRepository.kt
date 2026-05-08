package wizardry.compendium.essences

import kotlinx.coroutines.flow.Flow
import wizardry.compendium.essences.model.CharacterBuild

interface CharacterBuildRepository {
    val builds: Flow<List<CharacterBuild>>

    suspend fun getBuilds(): List<CharacterBuild>

    suspend fun getBuild(name: String): CharacterBuild?

    suspend fun saveBuildContribution(build: CharacterBuild): ContributionResult

    suspend fun deleteContribution(name: String): ContributionResult
}
