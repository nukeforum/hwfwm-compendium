package wizardry.compendium.characterbuild.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import wizardry.compendium.repositories.CharacterBuildRepository
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.ui.coroutines.IoDispatcher
import javax.inject.Inject

@HiltViewModel
class CharacterBuildSearchViewModel @Inject constructor(
    repository: CharacterBuildRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val buildsFlow = MutableStateFlow(emptyList<CharacterBuild>())
    private val filterTermFlow = MutableStateFlow("")

    private val _state = MutableStateFlow<CharacterBuildSearchUiState>(CharacterBuildSearchUiState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(buildsFlow, filterTermFlow) { builds, term ->
                CharacterBuildSearchUiState.Success(
                    builds = builds.filter { it.name.contains(term, ignoreCase = true) },
                    filterTerm = term,
                )
            }
                .onEach { _state.emit(it) }
                .collect()
        }

        viewModelScope.launch(ioDispatcher) {
            repository.builds.collect { buildsFlow.emit(it) }
        }
    }

    fun setFilterTerm(term: String) {
        viewModelScope.launch { filterTermFlow.emit(term) }
    }
}
