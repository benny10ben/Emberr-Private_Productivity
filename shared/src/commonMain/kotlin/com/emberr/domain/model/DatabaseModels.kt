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
    IS_NOT_EMPTY("is not empty"),
    IS_CHECKED("is checked"),
    IS_UNCHECKED("is unchecked"),
    IS_GREATER_THAN("is greater than"),
    IS_LESS_THAN("is less than");

    val needsValue: Boolean get() = this !in listOf(IS_EMPTY, IS_NOT_EMPTY, IS_CHECKED, IS_UNCHECKED)
}

@Serializable
enum class DatabaseViewType(val label: String) {
    TABLE("Table"),
    GALLERY("Gallery"),
    BOARD("Board")
}

@Serializable
enum class DatabaseDateGrouping(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year")
}

@Serializable
enum class DatabaseCardSize(val label: String) {
    SMALL("Small"),
    MEDIUM("Medium"),
    LARGE("Large")
}

@Immutable
@Serializable
data class DatabaseView(
    val id: String,
    val name: String,
    val type: DatabaseViewType,
    val showsIcon: Boolean = true,
    val showsCoverImage: Boolean = true,
    val cardSize: DatabaseCardSize = DatabaseCardSize.MEDIUM,
    val hiddenColumnKeys: List<String> = emptyList(),
    val groupByColumnKey: String? = null,
    val dateGrouping: DatabaseDateGrouping = DatabaseDateGrouping.MONTH,
    val hidesEmptyGroups: Boolean = false,
    val hiddenGroupKeys: List<String> = emptyList(),
    val collapsedGroupKeys: List<String> = emptyList(),
    val groupCalculationColumnKey: String? = null,
    val groupCalculation: DatabaseCalculation? = null,
    val manualRowOrder: List<String> = emptyList()
)

const val DEFAULT_VIEW_ID = "default-table"

enum class DatabaseCalculationGroup(val label: String) {
    COUNT("Count"),
    PERCENT("Percent"),
    MORE("More options")
}

@Serializable
enum class DatabaseCalculation(val label: String, val shortLabel: String, val group: DatabaseCalculationGroup) {
    COUNT_ALL("Count all", "Count", DatabaseCalculationGroup.COUNT),
    COUNT_VALUES("Count values", "Values", DatabaseCalculationGroup.COUNT),
    COUNT_UNIQUE_VALUES("Count unique values", "Unique", DatabaseCalculationGroup.COUNT),
    COUNT_EMPTY("Count empty", "Empty", DatabaseCalculationGroup.COUNT),
    COUNT_NOT_EMPTY("Count not empty", "Not empty", DatabaseCalculationGroup.COUNT),
    CHECKED("Checked", "Checked", DatabaseCalculationGroup.COUNT),
    UNCHECKED("Unchecked", "Unchecked", DatabaseCalculationGroup.COUNT),
    PERCENT_EMPTY("Percent empty", "Empty", DatabaseCalculationGroup.PERCENT),
    PERCENT_NOT_EMPTY("Percent not empty", "Not empty", DatabaseCalculationGroup.PERCENT),
    PERCENT_CHECKED("Percent checked", "Checked", DatabaseCalculationGroup.PERCENT),
    PERCENT_UNCHECKED("Percent unchecked", "Unchecked", DatabaseCalculationGroup.PERCENT),
    SUM("Sum", "Sum", DatabaseCalculationGroup.MORE),
    AVERAGE("Average", "Average", DatabaseCalculationGroup.MORE),
    MEDIAN("Median", "Median", DatabaseCalculationGroup.MORE),
    MIN("Min", "Min", DatabaseCalculationGroup.MORE),
    MAX("Max", "Max", DatabaseCalculationGroup.MORE),
    RANGE("Range", "Range", DatabaseCalculationGroup.MORE)
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

@Immutable
@Serializable
data class DatabaseSettingTime(
    val updatedAt: Long,
    val isDeleted: Boolean = false
)
