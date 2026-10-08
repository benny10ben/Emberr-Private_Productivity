package com.emberr.domain.selfhost.sync

import android.content.Context
import android.net.ConnectivityManager

class AndroidMeteredNetworkChecker(context: Context) : MeteredNetworkChecker {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override fun isOnMeteredNetwork(): Boolean = connectivityManager.isActiveNetworkMetered
}
