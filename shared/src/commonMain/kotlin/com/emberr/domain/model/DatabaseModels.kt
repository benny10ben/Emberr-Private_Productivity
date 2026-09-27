package com.emberr.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val NOTES_COLUMN_KEY = "notes"

@Immutable
@Serializable
sealed class DatabaseColumnTarget {

    @Immutable
    @Serializable
    @SerialName("notes_title")
    data object NotesTitle : DatabaseColumnTarget()

    @Immutable
    @Serializable
    @SerialName("property_column")
    data class Property(val propertyType: PropertyType) : DatabaseColumnTarget()

    @Immutable
    @Serializable
    @SerialName("custom_property_column")
    data class CustomProperty(val propertyId: String) : DatabaseColumnTarget()
}

val DatabaseColumnTarget.columnKey: String
    get() = when (this) {
        DatabaseColumnTarget.NotesTitle -> NOTES_COLUMN_KEY
        is DatabaseColumnTarget.Property -> propertyType.name
        is DatabaseColumnTarget.CustomProperty -> propertyId
    }

val DatabaseColumnTarget.tagPoolKey: String?
    get() = when (this) {
        DatabaseColumnTarget.NotesTitle -> null
        is DatabaseColumnTarget.Property -> propertyType.name
        is DatabaseColumnTarget.CustomProperty -> propertyId
    }

@Immutable
@Serializable
data class DatabaseCustomProperty(
    val id: String,
    val name: String,
    val valueType: PropertyValueType
)

fun DatabaseBlock.customPropertyWithId(propertyId: String): DatabaseCustomProperty? =
    customProperties.firstOrNull { it.id == propertyId }

fun DatabaseBlock.labelOf(column: DatabaseColumnTarget): String = when (column) {
    DatabaseColumnTarget.NotesTitle -> "Notes"
    is DatabaseColumnTarget.Property -> column.propertyType.label
    is DatabaseColumnTarget.CustomProperty -> customPropertyWithId(column.propertyId)?.name.orEmpty()
}

fun DatabaseBlock.valueTypeOf(column: DatabaseColumnTarget): PropertyValueType? = when (column) {
    DatabaseColumnTarget.NotesTitle -> PropertyValueType.TEXT
    is DatabaseColumnTarget.Property -> column.propertyType.valueType
    is DatabaseColumnTarget.CustomProperty -> customPropertyWithId(column.propertyId)?.valueType
}

@Serializable
enum class DatabaseFilterCondition(val label: String) {
    CONTAINS("contains"),
    DOES_NOT_CONTAIN("does not contain"),
    IS("is"),
    IS_NOT("is not"),
    IS_BEFORE("is before"),
    IS_AFTER("is after"),
    IS_EMPTY("is empty"),
    IS_NOT_EMPTY("is not empty");

    val needsValue: Boolean get() = this != IS_EMPTY && this != IS_NOT_EMPTY
}

@Immutable
@Serializable
data class DatabaseFilter(
    val id: String,
    val target: DatabaseColumnTarget,
    val condition: DatabaseFilterCondition,
    val text: String = "",
    val date: LocalDate? = null,
    val tagName: String? = null
)

@Immutable
@Serializable
data class DatabaseSort(
    val target: DatabaseColumnTarget,
    val isDescending: Boolean = false
)
