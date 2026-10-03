package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.domain.database.FormulaFunction
import com.emberr.domain.database.FormulaValue
import com.emberr.domain.database.formulaOf
import com.emberr.domain.database.formulaProblem
import com.emberr.domain.database.formulaTextLiteral
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseNumberFormat
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.TextAlignment
import com.emberr.domain.model.labelOf
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import com.emberr.presentation.shared.editor.blockViews.property.formatPropertyDate
import com.emberr.presentation.shared.editor.blockViews.property.toTextAlign
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.sigma
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val DesktopFormulaMenuWidth = 360.dp
private val FormulaPieceMinWidth = 36.dp
private val FormulaPieceMaxWidth = 220.dp
private val FormulaPieceGap = 6.dp

internal data class FormulaPiece(val label: String, val text: String, val cursorOffset: Int = text.length)

private val formulaOperationPieces = listOf(
    FormulaPiece("+", " + "),
    FormulaPiece("−", " - "),
    FormulaPiece("×", " * "),
    FormulaPiece("÷", " / "),
    FormulaPiece("(", "("),
    FormulaPiece(")", ")"),
    FormulaPiece(",", ", "),
    FormulaPiece("=", " == "),
    FormulaPiece("≠", " != "),
    FormulaPiece(">", " > "),
    FormulaPiece("<", " < "),
    FormulaPiece("≥", " >= "),
    FormulaPiece("≤", " <= "),
    FormulaPiece("\"text\"", "\"\"", cursorOffset = 1)
)

internal fun formulaPropertyPiece(propertyName: String): FormulaPiece =
    FormulaPiece(label = propertyName, text = "prop(${formulaTextLiteral(propertyName)})")

internal fun FormulaFunction.piece(): FormulaPiece {
    val text = when (this) {
        FormulaFunction.IF -> "if(, , )"
        FormulaFunction.DATE_ADD -> "dateAdd(, 1, \"days\")"
        FormulaFunction.DATE_BETWEEN -> "dateBetween(, now(), \"days\")"
        FormulaFunction.FORMAT_DATE -> "formatDate(, \"MMM D, YYYY\")"
        FormulaFunction.IFS -> "ifs(, , )"
        else -> "$formulaName()"
    }
    val cursorOffset = if (argumentCounts.last == 0) text.length else formulaName.length + 1
    return FormulaPiece(label = formulaName, text = text, cursorOffset = cursorOffset)
}

internal fun TextFieldValue.withFormulaPieceInserted(piece: FormulaPiece): TextFieldValue {
    val insertStart = selection.min
    val updatedText = text.replaceRange(insertStart, selection.max, piece.text)
    return TextFieldValue(updatedText, TextRange(insertStart + piece.cursorOffset))
}

internal fun FormulaValue.textToShow(): String = when (this) {
    is FormulaValue.DateValue -> formatPropertyDate(date)
    is FormulaValue.Error -> message
    else -> displayText
}

@Composable
internal fun DatabaseEditFormulaOption(
    block: DatabaseBlock,
    column: DatabaseColumnTarget,
    editor: DatabaseBlockEditor,
    closeMenuAnd: (() -> Unit) -> Unit
) {
    DatabaseMenuLayer(
        title = "Edit formula",
        desktopWidth = DesktopFormulaMenuWidth,
        showsCloseButton = false,
        anchor = { openLayer ->
            DatabaseMenuOption(
                label = "Edit formula",
                icon = { DatabaseOptionIcon(Res.drawable.sigma) },
                onClick = openLayer
            )
        }
    ) { closeLayerAnd ->
        DatabaseFormulaPage(
            block = block,
            column = column,
            savedFormula = block.formulaOf(column),
            onCancel = { closeLayerAnd { } },
            onSave = { formula -> closeLayerAnd { closeMenuAnd { editor.setFormula(block.id, column, formula) } } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DatabaseFormulaPage(
    block: DatabaseBlock,
    column: DatabaseColumnTarget,
    savedFormula: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit
) {
    var formula by remember(savedFormula) { mutableStateOf(TextFieldValue(savedFormula, TextRange(savedFormula.length))) }
    val problem = remember(formula.text, block) { block.formulaProblem(formula.text) }
    val propertyColumns = remember(block, column) {
        (listOf(DatabaseColumnTarget.NotesTitle) + block.columns).filter { it != column && block.labelOf(it).isNotBlank() }
    }

    fun insert(piece: FormulaPiece) {
        formula = formula.withFormulaPieceInserted(piece)
    }

    DatabaseMenuContent {
        EmberrTextField(
            value = formula,
            onValueChange = { formula = it },
            placeholder = "Tap the pieces below or type",
            singleLine = false,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Text(
            text = problem ?: "Tap a property, an operation or a function to add it.",
            style = MaterialTheme.typography.labelSmall,
            color = if (problem == null) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )

        FormulaPiecesSectionLabel(text = "Properties")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FormulaPieceGap), verticalArrangement = Arrangement.spacedBy(FormulaPieceGap)) {
            propertyColumns.forEach { propertyColumn ->
                val propertyName = block.labelOf(propertyColumn)
                FormulaPieceButton(
                    label = propertyName,
                    icon = block.iconOf(propertyColumn),
                    onClick = { insert(formulaPropertyPiece(propertyName)) }
                )
            }
        }

        FormulaPiecesSectionLabel(text = "Operations")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FormulaPieceGap), verticalArrangement = Arrangement.spacedBy(FormulaPieceGap)) {
            formulaOperationPieces.forEach { piece ->
                FormulaPieceButton(label = piece.label, onClick = { insert(piece) })
            }
        }

        FormulaPiecesSectionLabel(text = "Functions")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FormulaPieceGap), verticalArrangement = Arrangement.spacedBy(FormulaPieceGap)) {
            FormulaFunction.entries.forEach { function ->
                val piece = function.piece()
                FormulaPieceButton(label = piece.label, onClick = { insert(piece) })
            }
        }
    }
    DatabaseMenuButtons(cancelText = "Cancel", onCancel = onCancel, confirmText = "Save", onConfirm = { onSave(formula.text) })
}

@Composable
private fun FormulaPiecesSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun FormulaPieceButton(label: String, onClick: () -> Unit, icon: DrawableResource? = null) {
    Row(
        modifier = Modifier
            .widthIn(min = FormulaPieceMinWidth, max = FormulaPieceMaxWidth)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = if (isDesktopPlatform) 5.dp else 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun DatabaseFormulaValue(
    result: FormulaValue,
    textColor: Color?,
    alignment: TextAlignment?,
    modifier: Modifier = Modifier,
    numberFormat: DatabaseNumberFormat? = null,
    wrapsText: Boolean = true
) {
    val paddedModifier = modifier.padding(horizontal = DatabaseCellHorizontalPadding, vertical = DatabaseCellVerticalPadding)
    if (result is FormulaValue.NumberValue && numberFormat != null) {
        DatabaseNumberValue(number = result.number, format = numberFormat, textColor = textColor, alignment = alignment, modifier = paddedModifier)
        return
    }
    if (result is FormulaValue.BooleanValue) {
        Box(modifier = paddedModifier, contentAlignment = alignment.toCenteredBoxAlignment()) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Checkbox(
                        checked = result.isTrue,
                        onCheckedChange = null,
                        modifier = Modifier.scale(0.9f).size(16.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.surface,
                            checkmarkColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
        }
        return
    }

    val isError = result is FormulaValue.Error
    Text(
        text = result.textToShow(),
        style = if (isError) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyLarge,
        color = if (isError) MaterialTheme.colorScheme.error else textColor ?: MaterialTheme.colorScheme.onBackground,
        textAlign = alignment.toTextAlign(),
        maxLines = if (wrapsText) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Ellipsis,
        modifier = paddedModifier
    )
}

private fun TextAlignment?.toCenteredBoxAlignment(): Alignment = when (this) {
    TextAlignment.CENTER -> Alignment.Center
    TextAlignment.RIGHT -> Alignment.CenterEnd
    else -> Alignment.CenterStart
}
