package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseCalculation
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseCustomProperty
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseFilterCondition
import com.emberr.domain.model.DatabaseSort
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.TableBlock
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseFormulasTest {

    private val today = LocalDate(2026, 9, 28)
    private val priceColumn = DatabaseColumnTarget.CustomProperty("price")
    private val quantityColumn = DatabaseColumnTarget.CustomProperty("quantity")
    private val totalColumn = DatabaseColumnTarget.CustomProperty("total")
    private val formulaColumn = DatabaseColumnTarget.Property(PropertyType.FORMULA)
    private val doneColumn = DatabaseColumnTarget.Property(PropertyType.CHECKBOX)

    private val database = DatabaseBlock(
        id = "block",
        databaseId = "orders",
        columns = listOf(priceColumn, quantityColumn, totalColumn, formulaColumn, doneColumn),
        customProperties = listOf(
            DatabaseCustomProperty(id = "price", name = "Price", valueType = PropertyValueType.NUMBER),
            DatabaseCustomProperty(id = "quantity", name = "Quantity", valueType = PropertyValueType.NUMBER),
            DatabaseCustomProperty(id = "total", name = "Total", valueType = PropertyValueType.FORMULA)
        ),
        formulas = mapOf(
            "total" to """prop("Price") * prop("Quantity")""",
            PropertyType.FORMULA.name to """if(prop("Done"), prop("Total") * 2, 0)"""
        ),
        updatedAt = 100L
    )

    private fun numberCell(propertyId: String, noteId: String, text: String) = PropertyBlock(
        id = "$propertyId-$noteId",
        customPropertyId = propertyId,
        customValueType = PropertyValueType.NUMBER,
        text = text
    )

    private fun row(noteId: String, title: String = "", price: String? = null, quantity: String? = null, done: Boolean = false) =
        DatabaseRow(
            noteId = noteId,
            title = title,
            createdAt = 0L,
            cellsByColumn = listOfNotNull(
                price?.let { priceColumn to numberCell("price", noteId, it) },
                quantity?.let { quantityColumn to numberCell("quantity", noteId, it) },
                doneColumn to PropertyBlock(id = "checkbox-$noteId", propertyType = PropertyType.CHECKBOX, isChecked = done)
            ).toMap()
        )

    private fun DatabaseBlock.resultsFor(vararg rows: DatabaseRow): List<DatabaseRow> = rows.toList().withFormulaResults(this, today)

    private fun number(value: Double) = FormulaValue.NumberValue(value)

    @Test
    fun onlyTheFormulaPropertyIsKeptToDatabases() {
        assertEquals(listOf(PropertyType.FORMULA), PropertyType.entries.filter { it.isOnlyForDatabases })
        assertEquals(listOf(PropertyValueType.FORMULA), PropertyValueType.entries.filter { it.isOnlyForDatabases })
    }

    @Test
    fun formulaColumnsNeverCreateCellsInRows() {
        assertNull(database.emptyCell(formulaColumn, rowNoteId = "a", now = 1L))
        assertNull(database.emptyCell(totalColumn, rowNoteId = "a", now = 1L))
        assertEquals(
            listOf("price-a", "quantity-a", "checkbox-a"),
            database.newRowBlocks(rowNoteId = "a", now = 1L).map { it.id }
        )
    }

    @Test
    fun everyRowGetsItsOwnResults() {
        val results = database.resultsFor(row("a", price = "12.5", quantity = "4", done = true), row("b", price = "3", quantity = "2"))

        assertEquals(number(50.0), results[0].formulaResult(totalColumn))
        assertEquals(number(100.0), results[0].formulaResult(formulaColumn))
        assertEquals(number(6.0), results[1].formulaResult(totalColumn))
        assertEquals(number(0.0), results[1].formulaResult(formulaColumn))
    }

    @Test
    fun theNotesColumnAndPropertyNamesCanBeUsedInAnyCase() {
        val withTitleFormula = database.withFormulaSet(totalColumn, """concat(prop("notes"), ": ", prop("PRICE"))""")

        val result = withTitleFormula.resultsFor(row("a", title = "Tea", price = "3")).single().formulaResult(totalColumn)

        assertEquals(FormulaValue.TextValue("Tea: 3"), result)
    }

    @Test
    fun formulasThatUseEachOtherInACircleShowAnError() {
        val circular = database
            .withFormulaSet(totalColumn, """prop("Formula")""")
            .withFormulaSet(formulaColumn, """prop("Total")""")

        val result = circular.resultsFor(row("a")).single()

        assertEquals(FormulaValue.Error("This formula refers to itself"), result.formulaResult(totalColumn))
        assertEquals(FormulaValue.Error("This formula refers to itself"), result.formulaResult(formulaColumn))
    }

    @Test
    fun aMissingPropertyOrABrokenFormulaShowsAnError() {
        val broken = database
            .withFormulaSet(totalColumn, """prop("Cost")""")
            .withFormulaSet(formulaColumn, "2 +")

        val result = broken.resultsFor(row("a")).single()

        assertEquals(FormulaValue.Error("No property named \"Cost\""), result.formulaResult(totalColumn))
        assertEquals(FormulaValue.Error("The formula ends too early"), result.formulaResult(formulaColumn))
    }

    @Test
    fun formulaProblemChecksTheTextAndThePropertyNames() {
        assertNull(database.formulaProblem("""prop("Price") * 2"""))
        assertNull(database.formulaProblem(""))
        assertEquals("No property named \"Cost\"", database.formulaProblem("""prop("Cost") * 2"""))
        assertEquals("The formula ends too early", database.formulaProblem("2 +"))
    }

    @Test
    fun sortingByAFormulaComparesNumbersAsNumbers() {
        val sorted = database.withFormulaSet(totalColumn, """prop("Price")""").copy(sort = DatabaseSort(totalColumn))
        val rows = sorted.resultsFor(row("a", price = "100"), row("b", price = "9"), row("c", price = "10"))

        assertEquals(listOf("b", "c", "a"), applyFiltersAndSort(rows, sorted).map { it.noteId })
    }

    @Test
    fun filteringByAFormulaMatchesTheTextItShows() {
        val filtered = database.copy(
            filters = listOf(DatabaseFilter(id = "f", target = totalColumn, condition = DatabaseFilterCondition.CONTAINS, text = "50"))
        )
        val rows = filtered.resultsFor(row("a", price = "12.5", quantity = "4"), row("b", price = "3", quantity = "2"))

        assertEquals(listOf("a"), applyFiltersAndSort(rows, filtered).map { it.noteId })
    }

    @Test
    fun formulaColumnsCanBeSummedUp() {
        val rows = database.resultsFor(row("a", price = "12.5", quantity = "4"), row("b", price = "3", quantity = "2"))

        assertTrue(DatabaseCalculation.SUM in calculationsFor(PropertyValueType.FORMULA))
        assertEquals(
            DatabaseCalculationResult.NumberValue(56.0),
            calculate(rows, totalColumn, PropertyValueType.FORMULA, DatabaseCalculation.SUM)
        )
        assertEquals(
            DatabaseCalculationResult.Count(2),
            calculate(rows, totalColumn, PropertyValueType.FORMULA, DatabaseCalculation.COUNT_NOT_EMPTY)
        )
    }

    @Test
    fun settingABlankFormulaForgetsItAndOtherColumnsAreLeftAlone() {
        assertEquals(setOf(PropertyType.FORMULA.name), database.withFormulaSet(totalColumn, "  ").formulas.keys)
        assertEquals(database, database.withFormulaSet(priceColumn, "1 + 1"))
    }

    @Test
    fun renamingAPropertyUpdatesTheFormulasThatUseIt() {
        val withMixedCase = database.withFormulaSet(formulaColumn, """PROP( "price" ) + 1""")

        val renamed = withMixedCase.withDatabasePropertyRenamed("price", "Cost")

        assertEquals("""prop("Cost") * prop("Quantity")""", renamed.formulas["total"])
        assertEquals("""prop("Cost") + 1""", renamed.formulas[PropertyType.FORMULA.name])
    }

    @Test
    fun removingAFormulaColumnForgetsItsFormula() {
        assertEquals(setOf(PropertyType.FORMULA.name), database.withColumnRemoved(totalColumn).formulas.keys)
    }

    @Test
    fun formulasChangedOnTwoDevicesAreBothKept() {
        val synced = database.copy(formulas = emptyMap())
        val editedFirst = synced.withFormulaSet(formulaColumn, "1").copy(updatedAt = 200L).withSettingTimesStamped(before = synced, now = 200L)
        val editedSecond = synced.withFormulaSet(totalColumn, "2").copy(updatedAt = 300L).withSettingTimesStamped(before = synced, now = 300L)

        val merged = mergeDatabaseBlocks(editedFirst, editedSecond)

        assertEquals(mapOf("total" to "2", PropertyType.FORMULA.name to "1"), merged.formulas)
    }

    @Test
    fun exportedTablesShowFormulaResults() {
        val table = database.asExportBlocks(listOf(row("a", title = "Tea", price = "12.5", quantity = "4"))).single() as TableBlock

        assertEquals(listOf("Tea", "12.5", "4", "50", "0", ""), table.rows[1])
    }
}
