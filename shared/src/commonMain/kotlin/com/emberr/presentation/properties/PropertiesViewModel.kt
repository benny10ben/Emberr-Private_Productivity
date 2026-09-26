package com.emberr.presentation.properties

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emberr.data.local.room.entity.CustomPropertyEntity
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.withCustomPropertyRemoved
import com.emberr.domain.model.withCustomPropertyRenamed
import com.emberr.domain.repository.NoteRepository
import com.emberr.presentation.shared.editor.ActiveEditorRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PropertiesViewModel(
    private val repository: NoteRepository,
    private val appScope: CoroutineScope
) : ViewModel() {

    val customProperties: StateFlow<List<CustomPropertyEntity>> = repository.getCustomProperties()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun isNameTaken(name: String, ignoringPropertyId: String?): Boolean =
        PropertyType.entries.any { it.label.equals(name, ignoreCase = true) } ||
            customProperties.value.any { it.propertyId != ignoringPropertyId && it.name.equals(name, ignoreCase = true) }

    fun createProperty(name: String, valueType: PropertyValueType) {
        appScope.launch {
            try {
                repository.createCustomProperty(name, valueType)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun renameProperty(propertyId: String, newName: String) {
        changeEveryNote(
            rewriteOpenBlock = { block, now -> block.withCustomPropertyRenamed(propertyId, newName, now) },
            saveEverywhere = { repository.renameCustomProperty(propertyId, newName) }
        )
    }

    fun deleteProperty(propertyId: String) {
        changeEveryNote(
            rewriteOpenBlock = { block, now -> block.withCustomPropertyRemoved(propertyId, now) },
            saveEverywhere = { repository.deleteCustomProperty(propertyId) }
        )
    }

    private fun changeEveryNote(
        rewriteOpenBlock: (NoteBlock, Long) -> NoteBlock,
        saveEverywhere: suspend () -> Unit
    ) {
        appScope.launch(Dispatchers.Main) {
            try {
                ActiveEditorRegistry.rewriteBlocksInOpenEditors(rewriteOpenBlock)
                ActiveEditorRegistry.flushAllPending()
                saveEverywhere()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
