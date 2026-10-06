package com.emberr.data.local.room

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

actual suspend fun <R> AppDatabase.inOneTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
