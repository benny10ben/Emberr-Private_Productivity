package com.emberr.domain.sync.discovery

import com.emberr.core.desktop.DesktopAppStorage
import com.emberr.domain.sync.discoveryServiceTypeFor
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

class DesktopDiscoveryManager : SyncDiscoveryManager {
    private var jmdns: JmDNS? = null
    override val discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())

    override fun startBroadcasting(port: Int, deviceName: String) {
        try {
            val inetAddress = InetAddress.getLocalHost()
            jmdns = JmDNS.create(inetAddress)

            val serviceInfo = ServiceInfo.create(
                discoveryServiceTypeFor(DesktopAppStorage.isDebugBuild) + "local.",
                deviceName,
                port,
                "Emberr Desktop Sync Server"
            )

            jmdns?.registerService(serviceInfo)
            println("Broadcasting Emberr Sync on ${inetAddress.hostAddress}:$port")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stopBroadcasting() {
        jmdns?.unregisterAllServices()
        jmdns?.close()
        jmdns = null
    }

    override fun startScanning() { /* Not needed for Desktop */ }
    override fun stopScanning() { /* Not needed for Desktop */ }
}