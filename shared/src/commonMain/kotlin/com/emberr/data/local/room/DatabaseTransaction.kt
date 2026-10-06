package com.emberr.data.local.room

expect suspend fun <R> AppDatabase.inOneTransaction(block: suspend () -> R): R
