package com.emberr.presentation.shared.editor

import com.emberr.domain.model.CalloutBlock
import com.emberr.domain.model.CalloutType
import com.emberr.domain.model.TextBlock
import kotlin.test.Test
import kotlin.test.assertEquals

class CalloutFramesTest {

    @Test
    fun aCalloutFrameRunsFromItsHeaderToItsLastIndentedChild() {
        val blocks = listOf(
            TextBlock(id = "before", text = "Before"),
            CalloutBlock(id = "callout", calloutTypeName = CalloutType.TIP.name),
            TextBlock(id = "child-1", indentationLevel = 1),
            TextBlock(id = "child-2", indentationLevel = 1),
            TextBlock(id = "after", text = "After")
        )

        assertEquals(
            listOf(
                emptyList(),
                listOf(CalloutFrame(CalloutType.TIP, 0, 0, isFirstRow = true, isLastRow = false)),
                listOf(CalloutFrame(CalloutType.TIP, 0, 0, isFirstRow = false, isLastRow = false)),
                listOf(CalloutFrame(CalloutType.TIP, 0, 0, isFirstRow = false, isLastRow = true)),
                emptyList()
            ),
            calloutFramesByRow(blocks)
        )
    }

    @Test
    fun aCalloutWithNoChildrenIsBothItsFirstAndLastRow() {
        val blocks = listOf(
            CalloutBlock(id = "callout", calloutTypeName = CalloutType.NOTE.name),
            TextBlock(id = "after")
        )

        assertEquals(
            listOf(
                listOf(CalloutFrame(CalloutType.NOTE, 0, 0, isFirstRow = true, isLastRow = true)),
                emptyList()
            ),
            calloutFramesByRow(blocks)
        )
    }

    @Test
    fun aCalloutInsideACalloutIsShownOneLevelLessIndentedAndOneStepDeeper() {
        val blocks = listOf(
            CalloutBlock(id = "outer", calloutTypeName = CalloutType.INFO.name),
            CalloutBlock(id = "inner", calloutTypeName = CalloutType.BUG.name, indentationLevel = 1),
            TextBlock(id = "inner-child", indentationLevel = 2),
            TextBlock(id = "outer-child", indentationLevel = 1)
        )

        assertEquals(
            listOf(
                listOf(CalloutFrame(CalloutType.INFO, 0, 0, isFirstRow = true, isLastRow = false)),
                listOf(
                    CalloutFrame(CalloutType.INFO, 0, 0, isFirstRow = false, isLastRow = false),
                    CalloutFrame(CalloutType.BUG, 0, 1, isFirstRow = true, isLastRow = false)
                ),
                listOf(
                    CalloutFrame(CalloutType.INFO, 0, 0, isFirstRow = false, isLastRow = false),
                    CalloutFrame(CalloutType.BUG, 0, 1, isFirstRow = false, isLastRow = true)
                ),
                listOf(CalloutFrame(CalloutType.INFO, 0, 0, isFirstRow = false, isLastRow = true))
            ),
            calloutFramesByRow(blocks)
        )
    }

    @Test
    fun aCalloutsBodyIsShownLinedUpWithItsHeader() {
        val blocks = listOf(
            CalloutBlock(id = "outer", calloutTypeName = CalloutType.INFO.name, indentationLevel = 1),
            TextBlock(id = "body", indentationLevel = 2),
            TextBlock(id = "indented-body", indentationLevel = 3),
            CalloutBlock(id = "inner", calloutTypeName = CalloutType.BUG.name, indentationLevel = 2),
            TextBlock(id = "inner-body", indentationLevel = 3),
            TextBlock(id = "outside", indentationLevel = 1)
        )
        val framesByRow = calloutFramesByRow(blocks)

        val displayedLevels = blocks.mapIndexed { row, block -> displayedIndentationLevelOf(block, framesByRow[row]) }

        assertEquals(listOf(1, 1, 2, 1, 1, 1), displayedLevels)
    }
}
