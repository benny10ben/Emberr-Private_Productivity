package com.emberr.domain.sync

import com.emberr.data.local.prefs.SyncConstants

private const val DEBUG_BUILD_SYNC_PORT = 8081

fun defaultSyncPortFor(isDebugBuild: Boolean): Int =
    if (isDebugBuild) DEBUG_BUILD_SYNC_PORT else SyncConstants.DEFAULT_PORT

fun discoveryServiceTypeFor(isDebugBuild: Boolean): String =
    if (isDebugBuild) "_emberrdebug._tcp." else "_emberrsync._tcp."
