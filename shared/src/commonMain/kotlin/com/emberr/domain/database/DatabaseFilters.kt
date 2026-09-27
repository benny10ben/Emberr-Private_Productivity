package com.emberr.domain.database

import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.PropertyValueType

fun newDatabaseFilter(id: String, target: DatabaseColumnTarget, valueType: PropertyValueType): DatabaseFilter =
    DatabaseFilter(id = id, target = target, condition = filterConditionsFor(valueType).first())

fun DatabaseFilter.withTarget(newTarget: DatabaseColumnTarget, newValueType: PropertyValueType): DatabaseFilter =
    if (newTarget == target) this else newDatabaseFilter(id, newTarget, newValueType)
