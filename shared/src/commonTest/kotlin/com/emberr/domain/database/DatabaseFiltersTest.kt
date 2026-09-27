package com.emberr.domain.database

import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseFilterCondition
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.PropertyValueType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DatabaseFiltersTest {

    @Test
    fun aNewFilterStartsWithTheFirstConditionForItsColumn() {
        assertEquals(DatabaseFilterCondition.CONTAINS, newDatabaseFilter("f1", DatabaseColumnTarget.NotesTitle, PropertyValueType.TEXT).condition)
        assertEquals(
            DatabaseFilterCondition.IS,
            newDatabaseFilter("f2", DatabaseColumnTarget.Property(PropertyType.DUE_DATE), PropertyValueType.DATE).condition
        )
        assertEquals(
            DatabaseFilterCondition.IS,
            newDatabaseFilter("f3", DatabaseColumnTarget.CustomProperty("client-id"), PropertyValueType.SINGLE_CHOICE).condition
        )
    }

    @Test
    fun changingAFiltersColumnStartsItOverButKeepsItsId() {
        val filter = newDatabaseFilter("f1", DatabaseColumnTarget.NotesTitle, PropertyValueType.TEXT).copy(text = "dune")

        val moved = filter.withTarget(DatabaseColumnTarget.Property(PropertyType.STATUS), PropertyValueType.SINGLE_CHOICE)

        assertEquals(newDatabaseFilter("f1", DatabaseColumnTarget.Property(PropertyType.STATUS), PropertyValueType.SINGLE_CHOICE), moved)
    }

    @Test
    fun choosingTheSameColumnAgainChangesNothing() {
        val filter = newDatabaseFilter("f1", DatabaseColumnTarget.NotesTitle, PropertyValueType.TEXT).copy(text = "dune")

        assertSame(filter, filter.withTarget(DatabaseColumnTarget.NotesTitle, PropertyValueType.TEXT))
    }
}
