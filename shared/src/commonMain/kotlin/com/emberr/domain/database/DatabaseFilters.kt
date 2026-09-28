package com.emberr.domain.database

import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.PropertyValueType

fun newDatabaseFilter(id: String, target: DatabaseColumnTarget, valueType: PropertyValueType): DatabaseFilter =
    DatabaseFilter(id = id, target = target, condition = filterConditionsFor(valueType).first())
