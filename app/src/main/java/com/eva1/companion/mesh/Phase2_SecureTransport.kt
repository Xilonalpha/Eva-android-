package com.eva1.companion.mesh

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// ══════════════════════════════════════════════════════════════════════════════
// FAZA 2 — CANALUL DE TRANSPORT SECURIZAT SI CRIPTARE E2E (ECDH + AES-256-GCM)
// ══════════════════════════════════════════════════════════════════════════════

@Serializable
enum class PacketType {
    HANDSHAKE_PUBKEY,
    CAPABILITY_HEARTBEAT,
    TENSOR_FORWARD,
    TENSOR_RESULT,
    PIPELINE_ABORT,
    CHAT_MESSAGE
}

@Serializable
data class SwarmPacketHeader(
    val packetId: String,
    val type: PacketType,
    val senderNodeId: String,
    val targetNodeId: String,
    val sessionId: String = "",
    val layerFrom: Int = 0,
    val layerTo: Int = 0,
    val tensorShape: List<Int> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

class SwarmCryptoManager {
    private val keyPair: KeyPair by lazy {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        kpg.generateKeyPair()
    }

    private val peerSessionKeys = java.util.concurrent.ConcurrentHashMap<String, SecretKey>()

    private val defaultSwarmKey: SecretKey by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("EVA1_SWARM_FALLBACK_MASTER_KEY_2026".toByteArray())
        SecretKeySpec(digest, "AES")
    }

    fun getLocalPublicKeyBytes(): ByteArray = keyPair.public.encoded

    fun establishPeerSessionKey(peerNodeId: String, peerPublicKeyBytes: ByteArray) {
        val kf = KeyFactory.getInstance("EC")
        val peerPubKey = kf.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))

        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(keyPair.private)
        ka.doPhase(peerPubKey, true)
        val rawSharedSecret = ka.generateSecret()

        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec("EVA1-SWARM-HKDF-SALT".toByteArray(), "HmacSHA256"))
        val derivedKeyBytes = hmac.doFinal(rawSharedSecret)

        peerSessionKeys[peerNodeId] = SecretKeySpec(derivedKeyBytes, "AES")
    }

    fun encryptForPeer(peerNodeId: String?, plainBytes: ByteArray): ByteArray {
        val key = peerSessionKeys[peerNodeId] ?: defaultSwarmKey
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(plainBytes)
        return iv + cipherText
    }

    fun decryptFromPeer(peerNodeId: String?, encryptedBytes: ByteArray): ByteArray {
        require(encryptedBytes.size > 12) { "Pachet criptat prea scurt (${encryptedBytes.size} bytes)" }
        val key = (if (peerNodeId != null) peerSessionKeys[peerNodeId] else null) ?: defaultSwarmKey
        val iv = encryptedBytes.copyOfRange(0, 12)
        val body = encryptedBytes.copyOfRange(12, encryptedBytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }
}

object TensorSerializer {
    fun floatArrayToBytes(tensor: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(tensor.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(tensor)
        return buffer.array()
    }

    fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val floatBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val result = FloatArray(floatBuffer.remaining())
        floatBuffer.get(result)
        return result
    }
}

class SwarmTcpTransport(
    private val localPort: Int = 8988,
    private val crypto: SwarmCryptoManager,
    private val onPacketReceived: suspend (SwarmPacketHeader, ByteArray) -> Unit,
    private val onLog: (String) -> Unit
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startServer() {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(localPort)
                onLog("Server TCP Swarm activ pe portul $localPort (AES-256-GCM + ECDH)")
                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    launch { handleIncomingClient(client) }
                }
            } catch (e: Exception) {
                if (isActive) onLog("Server TCP oprit: ${e.message}")
            }
        }
    }

    private suspend fun handleIncomingClient(socket: Socket) {
        socket.use { sock ->
            try {
                sock.tcpNoDelay = true
                val input = DataInputStream(sock.getInputStream())

                val headerLen = input.readInt()
                require(headerLen in 2..65536) { "Dimensiune header invalidă: $headerLen" }
                val headerBytes = ByteArray(headerLen)
                input.readFully(headerBytes)
                val header = json.decodeFromString<SwarmPacketHeader>(headerBytes.decodeToString())

                val payloadLen = input.readInt()
                require(payloadLen in 0..(16 * 1024 * 1024)) { "Dimensiune payload invalidă: $payloadLen" }
                val encryptedPayload = ByteArray(payloadLen)
                input.readFully(encryptedPayload)

                val plainPayload = if (header.type == PacketType.HANDSHAKE_PUBKEY) {
                    crypto.establishPeerSessionKey(header.senderNodeId, encryptedPayload)
                    encryptedPayload
                } else {
                    crypto.decryptFromPeer(header.senderNodeId, encryptedPayload)
                }

                onPacketReceived(header, plainPayload)
            } catch (e: Exception) {
                onLog("Eroare recepție pachet TCP: ${e.message}")
            }
        }
    }

    suspend fun sendPacket(
        targetIp: String,
        targetPort: Int = 8988,
        header: SwarmPacketHeader,
        rawPayload: ByteArray,
        timeoutMs: Int = 5000
    ): Result<Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(targetIp, targetPort), timeoutMs)
                socket.soTimeout = timeoutMs

                val output = DataOutputStream(socket.getOutputStream())
                val headerBytes = json.encodeToString(header).encodeToByteArray()

                val wirePayload = if (header.type == PacketType.HANDSHAKE_PUBKEY) {
                    rawPayload
                } else {
                    crypto.encryptForPeer(header.targetNodeId, rawPayload)
                }

                output.writeInt(headerBytes.size)
                output.write(headerBytes)
                output.writeInt(wirePayload.size)
                output.write(wirePayload)
                output.flush()
            }
            Result.success(System.currentTimeMillis() - start)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun stopServer() {
        serverJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
    }
}
