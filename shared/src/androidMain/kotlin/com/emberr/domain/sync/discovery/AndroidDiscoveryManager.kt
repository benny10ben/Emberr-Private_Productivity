package com.emberr.domain.sync.discovery

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.emberr.domain.sync.discoveryServiceTypeFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class AndroidDiscoveryManager(context: Context) : SyncDiscoveryManager {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    override val discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())

    private val isDebugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val serviceType = discoveryServiceTypeFor(isDebugBuild)

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    override fun startScanning() {
        stopScanning()

        discoveredDevices.value = emptyList()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                val resolveListener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val ip = serviceInfo.host.hostAddress ?: return
                        val newDevice = DiscoveredDevice(serviceInfo.serviceName, ip, serviceInfo.port)

                        discoveredDevices.update { current ->
                            if (current.none { it.ipAddress == ip }) current + newDevice else current
                        }
                    }
                }

                try {
                    nsdManager.resolveService(service, resolveListener)
                } catch (e: Exception) {}
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopScanning()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopScanning()
            }
        }

        try {
            discoveryListener?.let {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stopScanning() {
        discoveryListener?.let { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: IllegalArgumentException) {
            }
        }
        discoveryListener = null
    }

    override fun startBroadcasting(port: Int, deviceName: String) { /* Not needed for Mobile */ }
    override fun stopBroadcasting() { /* Not needed for Mobile */ }
}