package com.emberr.domain.sync

const val LAN_SYNC_SCHEMA_VERSION = 2

const val OLDEST_SUPPORTED_LAN_SYNC_SCHEMA_VERSION = 2

fun isSupportedLanSyncSchemaVersion(peerSchemaVersion: Int): Boolean =
    peerSchemaVersion in OLDEST_SUPPORTED_LAN_SYNC_SCHEMA_VERSION..LAN_SYNC_SCHEMA_VERSION

class LanSyncSchemaMismatchException(message: String) : Exception(message)
