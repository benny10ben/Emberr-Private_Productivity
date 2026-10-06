package com.emberr.data.local.room

import androidx.room.withTransaction

actual suspend fun <R> AppDatabase.inOneTransaction(block: suspend () -> R): R =
    withTransaction(block)
