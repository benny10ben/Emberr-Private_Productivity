package com.emberr.domain.backup.manual

interface BackupRepository {

    suspend fun createBackupData(): EmberrBackupData
}
