package com.emberr.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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
    IS_ON_OR_BEFORE("is on or before"),
    IS_ON_OR_AFTER("is on or after"),
    IS_WITHIN("is within"),
    IS_EMPTY("is empty"),
    IS_NOT_EMPTY("is not empty"),
    IS_CHECKED("is checked"),
    IS_UNCHECKED("is unchecked"),
    IS_GREATER_THAN("is greater than"),
    IS_LESS_THAN("is less than");

    val needsValue: Boolean get() = this !in listOf(IS_EMPTY, IS_NOT_EMPTY, IS_CHECKED, IS_UNCHECKED)
}

@Serializable
enum class DatabaseRelativeDate(val label: String) {
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    YESTERDAY("Yesterday"),
    ONE_WEEK_AGO("One week ago"),
    ONE_WEEK_FROM_NOW("One week from now"),
    ONE_MONTH_AGO("One month ago"),
    ONE_MONTH_FROM_NOW("One month from now")
}

@Serializable
enum class DatabaseDateRange(val label: String) {
    THIS_WEEK("This week"),
    THIS_MONTH("This month"),
    THIS_YEAR("This year"),
    PAST_7_DAYS("The past 7 days"),
    NEXT_7_DAYS("The next 7 days"),
    PAST_30_DAYS("The past 30 days"),
    NEXT_30_DAYS("The next 30 days")
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
    val manualRowOrder: List<String> = emptyList(),
    val filters: List<DatabaseFilter> = emptyList(),
    val sorts: List<DatabaseSort> = emptyList(),
    val freezesTitleColumn: Boolean = false,
    val wrapsCellText: Boolean = true
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
    val relativeDate: DatabaseRelativeDate? = null,
    val dateRange: DatabaseDateRange? = null,
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
data class DatabaseCellStyle(
    val textColorName: String? = null,
    val backgroundColorName: String? = null,
    val alignment: TextAlignment? = null
)

@Serializable
enum class DatabaseColorRuleTarget(val label: String) {
    ROW("Whole row"),
    CELL("Only this cell")
}

@Immutable
@Serializable
data class DatabaseColorRule(
    val id: String,
    val condition: DatabaseFilter,
    val target: DatabaseColorRuleTarget = DatabaseColorRuleTarget.ROW,
    val textColorName: String? = null,
    val backgroundColorName: String? = null
)

@Serializable
enum class DatabaseRepeatFrequency(val label: String) {
    DAILY("Every day"),
    WEEKDAYS("Every weekday"),
    WEEKLY("Every week"),
    MONTHLY("Every month")
}

@Immutable
@Serializable
data class DatabaseTemplateRepeat(
    val frequency: DatabaseRepeatFrequency,
    val startsOn: LocalDate,
    val isoDaysOfWeek: List<Int> = emptyList(),
    val time: LocalTime? = null,
    val skipsFirstDay: Boolean = false
)

@Serializable
enum class DatabaseNumberStyle(
    val label: String,
    val prefix: String = "",
    val suffix: String = "",
    val groupsThousands: Boolean = true,
    val defaultDecimalPlaces: Int? = null
) {
    NUMBER("Number", groupsThousands = false),
    NUMBER_WITH_COMMAS("Number with commas"),
    PERCENT("Percent", suffix = "%"),
    US_DOLLAR("US dollar", prefix = "$", defaultDecimalPlaces = 2),
    EURO("Euro", prefix = "€", defaultDecimalPlaces = 2),
    POUND("Pound", prefix = "£", defaultDecimalPlaces = 2),
    RUPEE("Rupee", prefix = "₹", defaultDecimalPlaces = 2),
    YEN("Yen", prefix = "¥", defaultDecimalPlaces = 0)
}

@Serializable
enum class DatabaseNumberDisplay(val label: String) {
    NUMBER("Number"),
    BAR("Bar"),
    RING("Ring")
}

@Immutable
@Serializable
data class DatabaseNumberFormat(
    val style: DatabaseNumberStyle = DatabaseNumberStyle.NUMBER,
    val decimalPlaces: Int? = null,
    val display: DatabaseNumberDisplay = DatabaseNumberDisplay.NUMBER,
    val progressGoal: Double = 100.0,
    val showsNumberWithProgress: Boolean = true
)

@Immutable
@Serializable
data class DatabaseSettingTime(
    val updatedAt: Long,
    val isDeleted: Boolean = false
)
