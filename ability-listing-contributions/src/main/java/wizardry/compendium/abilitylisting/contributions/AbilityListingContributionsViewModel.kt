package wizardry.compendium.abilitylisting.contributions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AbilityType
import wizardry.compendium.essences.model.Cost
import wizardry.compendium.essences.model.Effect
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.Rank
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class AbilityListingContributionsViewModel @Inject constructor(
    private val abilityListingRepository: AbilityListingRepository,
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    private val _effects = MutableStateFlow<List<EffectDraft>>(emptyList())
    val effects = _effects.asStateFlow()

    fun appendEffect() {
        _effects.update { it + EffectDraft() }
    }

    fun removeEffect(index: Int) {
        _effects.update { current ->
            if (index !in current.indices) current
            else current.toMutableList().apply { removeAt(index) }
        }
    }

    fun updateEffect(index: Int, transform: (EffectDraft) -> EffectDraft) {
        _effects.update { current ->
            if (index !in current.indices) current
            else current.toMutableList().apply { this[index] = transform(this[index]) }
        }
    }

    fun saveAbilityListing(name: String) {
        if (name.isBlank()) {
            viewModelScope.launch { _saveState.emit(SaveState.Error("Name cannot be empty")) }
            return
        }
        val drafts = _effects.value
        drafts.forEachIndexed { index, draft ->
            val label = "Effect ${index + 1}"
            if (draft.rank == null) return fail("$label: rank is required")
            if (draft.type == null) return fail("$label: type is required")
            if (draft.properties.isEmpty()) return fail("$label: at least one property is required")
            if (draft.description.isBlank()) return fail("$label: description is required")
            if (parseCooldown(draft.cooldown) == null) return fail("$label: cooldown is invalid")
        }

        viewModelScope.launch(Dispatchers.IO) {
            _saveState.emit(SaveState.Saving)
            val effects = drafts.map { it.toEffect() }
            val listing = Ability.Listing.of(name = name.trim()).copy(effects = effects)
            when (val result = abilityListingRepository.saveAbilityListingContribution(listing)) {
                is ContributionResult.Success -> _saveState.emit(SaveState.Success)
                is ContributionResult.Failure -> _saveState.emit(SaveState.Error(result.message))
            }
        }
    }

    fun clearSaveState() {
        viewModelScope.launch { _saveState.emit(SaveState.Idle) }
    }

    private fun fail(message: String) {
        viewModelScope.launch { _saveState.emit(SaveState.Error(message)) }
    }

    sealed interface SaveState {
        data object Idle : SaveState
        data object Saving : SaveState
        data object Success : SaveState
        data class Error(val message: String) : SaveState
    }
}

data class EffectDraft(
    val rank: Rank? = null,
    val type: AbilityType? = null,
    val properties: List<Property> = emptyList(),
    val costs: List<Cost> = emptyList(),
    val cooldown: String = "",
    val replacementKey: String = "",
    val description: String = "",
)

private fun EffectDraft.toEffect(): Effect.AbilityEffect = Effect.AbilityEffect(
    rank = rank!!,
    type = type!!,
    properties = properties,
    cost = costs.ifEmpty { listOf(Cost.None) },
    cooldown = parseCooldown(cooldown) ?: Duration.ZERO,
    description = description.trim(),
    replacementKey = replacementKey.trim().takeIf { it.isNotEmpty() },
)

private val COOLDOWN_REGEX = Regex("^\\s*(\\d+)\\s*([a-zA-Z]+)\\s*$")

internal fun parseCooldown(input: String): Duration? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return Duration.ZERO
    val match = COOLDOWN_REGEX.matchEntire(trimmed) ?: return null
    val value = match.groupValues[1].toLongOrNull() ?: return null
    return when (match.groupValues[2].lowercase()) {
        "s", "sec", "secs", "second", "seconds" -> value.seconds
        "min", "mins", "minute", "minutes" -> value.minutes
        "h", "hr", "hrs", "hour", "hours" -> value.hours
        "d", "day", "days" -> value.days
        "w", "wk", "wks", "week", "weeks" -> (value * 7).days
        "m", "mo", "month", "months" -> (value * 30).days
        "y", "yr", "yrs", "year", "years" -> (value * 365).days
        else -> null
    }
}
