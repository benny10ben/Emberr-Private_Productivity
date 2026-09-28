package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.emberr.domain.database.FormulaFunction
import com.emberr.domain.database.parseFormula
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseFormulaViewsTest {

    private fun formulaWithCursorAt(text: String, cursor: Int = text.length) = TextFieldValue(text, TextRange(cursor))

    @Test
    fun tappingPiecesOneAfterAnotherBuildsAWorkingFormula() {
        val formula = formulaWithCursorAt("")
            .withFormulaPieceInserted(formulaPropertyPiece("Price"))
            .withFormulaPieceInserted(FormulaPiece("×", " * "))
            .withFormulaPieceInserted(formulaPropertyPiece("Quantity"))

        assertEquals("""prop("Price") * prop("Quantity")""", formula.text)
        assertEquals(TextRange(formula.text.length), formula.selection)
        parseFormula(formula.text)
    }

    @Test
    fun aPieceGoesWhereTheCursorIs() {
        val formula = formulaWithCursorAt("""prop("Price")""", cursor = 0).withFormulaPieceInserted(FormulaPiece("(", "("))

        assertEquals("""(prop("Price")""", formula.text)
        assertEquals(TextRange(1), formula.selection)
    }

    @Test
    fun aPieceReplacesSelectedText() {
        val formula = TextFieldValue("1 + 2", TextRange(2, 3)).withFormulaPieceInserted(FormulaPiece("×", "*"))

        assertEquals("1 * 2", formula.text)
        assertEquals(TextRange(3), formula.selection)
    }

    @Test
    fun functionsPutTheCursorInsideTheirBrackets() {
        val withIf = formulaWithCursorAt("").withFormulaPieceInserted(FormulaFunction.IF.piece())
        val withNow = formulaWithCursorAt("").withFormulaPieceInserted(FormulaFunction.NOW.piece())

        assertEquals("if(, , )", withIf.text)
        assertEquals(TextRange(3), withIf.selection)
        assertEquals("now()", withNow.text)
        assertEquals(TextRange(5), withNow.selection)
    }

    @Test
    fun propertyNamesWithQuotesAreEscaped() {
        assertEquals("""prop("Say \"hi\"")""", formulaPropertyPiece("Say \"hi\"").text)
    }
}
