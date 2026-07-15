package wizardry.compendium.racetemplate.search

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
import wizardry.compendium.domain.model.RaceTemplate
import wizardry.compendium.repositories.RaceTemplateRepository
import wizardry.compendium.ui.coroutines.IoDispatcher
import javax.inject.Inject

@HiltViewModel
class RaceTemplateSearchViewModel @Inject constructor(
    repository: RaceTemplateRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val templatesFlow = MutableStateFlow(emptyList<RaceTemplate>())
    private val filterTermFlow = MutableStateFlow("")

    private val _state = MutableStateFlow<RaceTemplateSearchUiState>(RaceTemplateSearchUiState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(templatesFlow, filterTermFlow) { templates, term ->
                RaceTemplateSearchUiState.Success(
                    templates = templates.filter { it.name.contains(term, ignoreCase = true) },
                    filterTerm = term,
                )
            }
                .onEach { _state.emit(it) }
                .collect()
        }

        viewModelScope.launch(ioDispatcher) {
            repository.raceTemplates.collect { templatesFlow.emit(it) }
        }
    }

    fun setFilterTerm(term: String) {
        viewModelScope.launch { filterTermFlow.emit(term) }
    }
}
