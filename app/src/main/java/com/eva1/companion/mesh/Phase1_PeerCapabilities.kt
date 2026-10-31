package com.eva1.companion.mesh

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

// ══════════════════════════════════════════════════════════════════════════════
// FAZA 1 — PROTOCOLUL DE DESCOPERIRE SI NEGOCIERE CAPABILITATI (mDNS / DNS-SD)
//
// Fiecare telefon din roi isi masoara in timp real resursele hardware
// (RAM liber, frecventa CPU, baterie, temperatura termica, NPU/GPU) si le
// publica prin Wi-Fi Direct DNS-SD (Zero-Configuration Networking) FARA router
// si FARA conexiune la internet.
// ══════════════════════════════════════════════════════════════════════════════

@Serializable
enum class NodeStatus {
    IDLE,
    COMPUTING,
    BUSY,
    OVERHEATING,
    LOW_BATTERY,
    OFFLINE
}

@Serializable
data class PeerCapability(
    val nodeId: String,
    val deviceName: String,
    val ipAddress: String = "",
    val port: Int = 8988,
    val availableRamMb: Long,
    val totalRamMb: Long,
    val cpuCores: Int,
    val cpuMaxFreqMhz: Long,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val thermalStatus: Int,
    val hasVulkanGpu: Boolean,
    val status: NodeStatus = NodeStatus.IDLE,
    val assignedLayerStart: Int = 0,
    val assignedLayerEnd: Int = 0,
    val rttLatencyMs: Long = 10L,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    fun computeScore(): Double {
        if (status == NodeStatus.OVERHEATING || status == NodeStatus.OFFLINE) return 0.01
        if (batteryPercent < 15 && !isCharging) return 0.05

        val ramFactor = (availableRamMb.toDouble() / 4096.0).coerceIn(0.1, 4.0)
        val cpuFactor = (cpuCores * (cpuMaxFreqMhz / 1000.0)) / 16.0
        val batteryFactor = if (isCharging) 1.2 else (batteryPercent / 100.0).coerceAtLeast(0.2)
        val thermalPenalty = when (thermalStatus) {
            0, 1 -> 1.0
            2 -> 0.6
            else -> 0.15
        }
        val latencyPenalty = (100.0 / (rttLatencyMs + 50.0)).coerceIn(0.2, 1.5)
        val gpuBonus = if (hasVulkanGpu) 1.35 else 1.0

        return (ramFactor * 0.40 + cpuFactor * 0.30 + batteryFactor * 0.15 + latencyPenalty * 0.15) *
            thermalPenalty * gpuBonus
    }
}

class HardwareCapabilityProfiler(private val context: Context) {

    val localNodeId: String by lazy {
        val prefs = context.getSharedPreferences("eva_mesh_prefs", Context.MODE_PRIVATE)
        prefs.getString("node_id", null) ?: UUID.randomUUID().toString().take(8).also {
            prefs.edit().putString("node_id", it).apply()
        }
    }

    fun snapshotLocalCapability(
        ipAddress: String = "192.168.49.1",
        port: Int = 8988,
        currentStatus: NodeStatus = NodeStatus.IDLE
    ): PeerCapability {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024 * 1024)
        val totalMb = memInfo.totalMem / (1024 * 1024)

        val cores = Runtime.getRuntime().availableProcessors()
        val maxFreqKhz = readCpuMaxFreqKhz()

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 50) ?: 50
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = (level * 100) / scale.coerceAtLeast(1)
        val plug = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val isCharging = plug != 0

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pm.currentThermalStatus
        } else 0

        val finalStatus = when {
            thermal >= 3 -> NodeStatus.OVERHEATING
            batteryPct < 12 && !isCharging -> NodeStatus.LOW_BATTERY
            else -> currentStatus
        }

        return PeerCapability(
            nodeId = localNodeId,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            ipAddress = ipAddress,
            port = port,
            availableRamMb = availMb,
            totalRamMb = totalMb,
            cpuCores = cores,
            cpuMaxFreqMhz = maxFreqKhz / 1000,
            batteryPercent = batteryPct,
            isCharging = isCharging,
            thermalStatus = thermal,
            hasVulkanGpu = context.packageManager.hasSystemFeature("android.hardware.vulkan.compute"),
            status = finalStatus
        )
    }

    private fun readCpuMaxFreqKhz(): Long {
        return try {
            val file = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (file.exists()) file.readText().trim().toLong() else 2400000L
        } catch (e: Exception) {
            2400000L
        }
    }
}

class WifiDirectDiscoveryService(
    private val context: Context,
    private val profiler: HardwareCapabilityProfiler
) {
    companion object {
        const val SERVICE_INSTANCE = "_eva1_swarm_ai"
        const val SERVICE_REG_TYPE = "_presence._tcp"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val wifiP2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }
    private var channel: WifiP2pManager.Channel? = null

    private val _discoveredPeers = MutableStateFlow<Map<String, PeerCapability>>(emptyMap())
    val discoveredPeers: StateFlow<Map<String, PeerCapability>> = _discoveredPeers.asStateFlow()

    fun initialize() {
        channel = wifiP2pManager?.initialize(context, context.mainLooper, null)
    }

    fun broadcastLocalCapabilities(ipAddress: String = "192.168.49.1", port: Int = 8988) {
        val mgr = wifiP2pManager ?: return
        val ch = channel ?: return
        val cap = profiler.snapshotLocalCapability(ipAddress, port)

        val record = mapOf(
            "node_id" to cap.nodeId,
            "name" to cap.deviceName,
            "ip" to cap.ipAddress,
            "port" to cap.port.toString(),
            "ram_mb" to cap.availableRamMb.toString(),
            "total_ram" to cap.totalRamMb.toString(),
            "cores" to cap.cpuCores.toString(),
            "freq_mhz" to cap.cpuMaxFreqMhz.toString(),
            "bat" to cap.batteryPercent.toString(),
            "chg" to if (cap.isCharging) "1" else "0",
            "therm" to cap.thermalStatus.toString(),
            "vulkan" to if (cap.hasVulkanGpu) "1" else "0",
            "status" to cap.status.name
        )

        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
            SERVICE_INSTANCE,
            SERVICE_REG_TYPE,
            record
        )

        mgr.clearLocalServices(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                mgr.addLocalService(ch, serviceInfo, null)
            }
            override fun onFailure(reason: Int) {
                mgr.addLocalService(ch, serviceInfo, null)
            }
        })
    }

    fun startDiscovery(onLog: (String) -> Unit) {
        val mgr = wifiP2pManager ?: return
        val ch = channel ?: return

        val txtListener = WifiP2pManager.DnsSdTxtRecordListener { fullDomain, record, device ->
            if (fullDomain.contains(SERVICE_INSTANCE)) {
                val cap = parseTxtRecord(record, device)
                _discoveredPeers.update { current -> current + (cap.nodeId to cap) }
                onLog("Nod descoperit prin DNS-SD: ${cap.deviceName} (${cap.availableRamMb}MB RAM liber, scor=${"%.2f".format(cap.computeScore())})")
            }
        }

        val servListener = WifiP2pManager.DnsSdServiceResponseListener { _, _, _ -> }

        mgr.setDnsSdResponseListeners(ch, servListener, txtListener)

        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        mgr.clearServiceRequests(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                mgr.addServiceRequest(ch, serviceRequest, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        mgr.discoverServices(ch, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() = onLog("Căutare DNS-SD Wi-Fi Direct activă...")
                            override fun onFailure(code: Int) = onLog("Eroare discoverServices: $code")
                        })
                    }
                    override fun onFailure(code: Int) = onLog("Eroare addServiceRequest: $code")
                })
            }
            override fun onFailure(code: Int) {}
        })
    }

    fun updatePeerFromNetwork(capability: PeerCapability) {
        _discoveredPeers.update { it + (capability.nodeId to capability) }
    }

    fun removeStalePeers(timeoutMs: Long = 30_000L) {
        val now = System.currentTimeMillis()
        _discoveredPeers.update { map ->
            map.filterValues { now - it.lastSeenTimestamp < timeoutMs }
        }
    }

    private fun parseTxtRecord(record: Map<String, String>, device: WifiP2pDevice): PeerCapability {
        return PeerCapability(
            nodeId = record["node_id"] ?: device.deviceAddress,
            deviceName = record["name"] ?: device.deviceName ?: "EVA-Peer",
            ipAddress = record["ip"] ?: "",
            port = record["port"]?.toIntOrNull() ?: 8988,
            availableRamMb = record["ram_mb"]?.toLongOrNull() ?: 1024L,
            totalRamMb = record["total_ram"]?.toLongOrNull() ?: 4096L,
            cpuCores = record["cores"]?.toIntOrNull() ?: 4,
            cpuMaxFreqMhz = record["freq_mhz"]?.toLongOrNull() ?: 2000L,
            batteryPercent = record["bat"]?.toIntOrNull() ?: 50,
            isCharging = record["chg"] == "1",
            thermalStatus = record["therm"]?.toIntOrNull() ?: 0,
            hasVulkanGpu = record["vulkan"] == "1",
            status = runCatching { NodeStatus.valueOf(record["status"] ?: "IDLE") }.getOrDefault(NodeStatus.IDLE)
        )
    }

    fun encodeCapabilityJson(cap: PeerCapability): String = json.encodeToString(cap)
    fun decodeCapabilityJson(raw: String): PeerCapability = json.decodeFromString(raw)
}
