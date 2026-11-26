package dev.localremote.app.connectivity.androidtv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap

data class AndroidTvEndpoint(
    val name: String,
    val host: String,
    val port: Int
)

/** Bounded mDNS discovery for the Android TV Remote v2 service; no network sweep is performed. */
class AndroidTvDiscovery(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val resolving = ConcurrentHashMap.newKeySet<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val stopScan = Runnable { stop() }

    @Synchronized
    fun start(onFound: (AndroidTvEndpoint) -> Unit, onError: (String) -> Unit) {
        stop()
        resolving.clear()
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains(SERVICE_TYPE_PREFIX)) return
                if (!resolving.add(serviceInfo.serviceName)) return
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                        resolving.remove(service.serviceName)
                    }

                    override fun onServiceResolved(service: NsdServiceInfo) {
                        resolving.remove(service.serviceName)
                        val host = service.host?.hostAddress ?: return
                        if (service.port == REMOTE_PORT) {
                            onFound(
                                AndroidTvEndpoint(
                                    name = service.serviceName,
                                    host = host,
                                    port = service.port
                                )
                            )
                        }
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                resolving.remove(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                onError("TV discovery could not start (network service error $errorCode).")
                runCatching { manager.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                onError("TV discovery could not stop cleanly (network service error $errorCode).")
            }
        }
        listener = discovery
        try {
            multicastLock = wifiManager.createMulticastLock("OneRemote-AndroidTV-mDNS").apply {
                setReferenceCounted(false)
                acquire()
            }
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
            mainHandler.postDelayed(stopScan, SCAN_WINDOW_MS)
        } catch (failure: RuntimeException) {
            releaseMulticastLock()
            listener = null
            onError(failure.message ?: "TV discovery is unavailable on this network.")
        }
    }

    @Synchronized
    fun stop() {
        mainHandler.removeCallbacks(stopScan)
        listener?.let { active ->
            runCatching { manager.stopServiceDiscovery(active) }
        }
        listener = null
        releaseMulticastLock()
    }

    override fun close() = stop()

    private fun releaseMulticastLock() {
        multicastLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        multicastLock = null
    }

    private companion object {
        const val SERVICE_TYPE = "_androidtvremote2._tcp."
        const val SERVICE_TYPE_PREFIX = "_androidtvremote2._tcp"
        const val REMOTE_PORT = 6466
        const val SCAN_WINDOW_MS = 15_000L
    }
}
