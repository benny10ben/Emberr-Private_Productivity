package com.emberr.data.local.room.entity

import androidx.room.Entity

@Entity(tableName = "unapplied_sync_changes", primaryKeys = ["entityType", "entityId"])
data class UnappliedSyncChangeEntity(
    val entityType: String,
    val entityId: String,
    val envelopeJson: String,
    val failedOnAppVersion: String,
    val failedAt: Long
)
