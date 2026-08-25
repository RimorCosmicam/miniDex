package com.minidex.app.input.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Automatically discovers Wireless Debugging pairing ports and connection ports
 * on localhost/local network via Android's NsdManager (mDNS).
 *
 * Services:
 * - Pairing: "_adb-tls-pairing._tcp" (Active when user opens "Pair with pairing code")
 * - Connection: "_adb-tls-connect._tcp" and "_adb._tcp" (Active when Wireless Debugging is enabled)
 */
class AdbMdnsDiscovery(private val context: Context) {

    companion object {
        private const val TAG = "AdbMdnsDiscovery"
        const val SERVICE_TYPE_PAIRING = "_adb-tls-pairing._tcp."
        const val SERVICE_TYPE_CONNECT_TLS = "_adb-tls-connect._tcp."
    }

    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private val _discoveredPairingPort = MutableStateFlow<Int?>(null)
    val discoveredPairingPort: StateFlow<Int?> = _discoveredPairingPort.asStateFlow()

    private val _discoveredConnectPort = MutableStateFlow<Int?>(null)
    val discoveredConnectPort: StateFlow<Int?> = _discoveredConnectPort.asStateFlow()

    private val _discoveredHost = MutableStateFlow<String>("127.0.0.1")
    val discoveredHost: StateFlow<String> = _discoveredHost.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var pairingDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var connectDiscoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        if (nsdManager == null) {
            Log.w(TAG, "NsdManager not available on this device")
            return
        }

        stopDiscovery()
        _isSearching.value = true

        // 1. Discover Pairing Port (_adb-tls-pairing._tcp)
        pairingDiscoveryListener = createDiscoveryListener(
            targetServiceType = "_adb-tls-pairing._tcp",
            onServiceResolved = { info ->
                val port = info.port
                val host = info.host?.hostAddress ?: "127.0.0.1"
                Log.d(TAG, "Discovered ADB Pairing Service: $host:$port (${info.serviceName})")
                _discoveredPairingPort.value = port
                _discoveredHost.value = host
            }
        )

        // 2. Discover Connect Port (_adb-tls-connect._tcp)
        connectDiscoveryListener = createDiscoveryListener(
            targetServiceType = "_adb-tls-connect._tcp",
            onServiceResolved = { info ->
                val port = info.port
                val host = info.host?.hostAddress ?: "127.0.0.1"
                Log.d(TAG, "Discovered ADB Connect Service: $host:$port (${info.serviceName})")
                _discoveredConnectPort.value = port
                _discoveredHost.value = host
            }
        )

        try {
            nsdManager?.discoverServices(SERVICE_TYPE_PAIRING, NsdManager.PROTOCOL_DNS_SD, pairingDiscoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start pairing discovery", e)
        }

        try {
            nsdManager?.discoverServices(SERVICE_TYPE_CONNECT_TLS, NsdManager.PROTOCOL_DNS_SD, connectDiscoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start connect discovery", e)
        }
    }

    fun stopDiscovery() {
        _isSearching.value = false

        pairingDiscoveryListener?.let {
            try {
                nsdManager?.stopServiceDiscovery(it)
            } catch (_: Exception) {}
            pairingDiscoveryListener = null
        }

        connectDiscoveryListener?.let {
            try {
                nsdManager?.stopServiceDiscovery(it)
            } catch (_: Exception) {}
            connectDiscoveryListener = null
        }
    }

    private fun createDiscoveryListener(
        targetServiceType: String,
        onServiceResolved: (NsdServiceInfo) -> Unit
    ): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started for $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${service.serviceName}, type: ${service.serviceType}")
                resolveService(service, onServiceResolved)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: $errorCode")
            }
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo, onResolved: (NsdServiceInfo) -> Unit) {
        try {
            nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Resolve failed for ${service.serviceName}: $errorCode")
                }

                override fun onServiceResolved(service: NsdServiceInfo) {
                    onResolved(service)
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initiate service resolve", e)
        }
    }
}
