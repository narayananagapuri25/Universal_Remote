package dev.localremote.app.connectivity.androidtv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import java.util.UUID
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

internal data class ProtoField(
    val number: Int,
    val wireType: Int,
    val numberValue: Long = 0,
    val bytesValue: ByteArray = byteArrayOf()
)

/** Minimal bounded Protocol Buffers wire codec for the Android TV Remote v2 messages we use. */
internal class ProtoWriter {
    private val output = ByteArrayOutputStream()

    fun varint(field: Int, value: Long): ProtoWriter {
        require(field > 0 && value >= 0)
        writeVarint((field shl 3).toLong())
        writeVarint(value)
        return this
    }

    fun bytes(field: Int, value: ByteArray): ProtoWriter {
        require(field > 0)
        writeVarint(((field shl 3) or 2).toLong())
        writeVarint(value.size.toLong())
        output.write(value)
        return this
    }

    fun string(field: Int, value: String): ProtoWriter =
        bytes(field, value.toByteArray(Charsets.UTF_8))

    fun toByteArray(): ByteArray = output.toByteArray()

    private fun writeVarint(raw: Long) {
        var value = raw
        while ((value and -128L) != 0L) {
            output.write((value.toInt() and 0x7f) or 0x80)
            value = value ushr 7
        }
        output.write(value.toInt())
    }
}

internal object ProtoReader {
    fun parse(bytes: ByteArray): List<ProtoField> {
        val input = ByteArrayInputStream(bytes)
        val fields = mutableListOf<ProtoField>()
        while (input.available() > 0) {
            val tag = readVarint(input).toInt()
            require(tag != 0) { "Invalid protobuf field tag." }
            val number = tag ushr 3
            val wire = tag and 7
            require(number > 0) { "Invalid protobuf field number." }
            when (wire) {
                0 -> fields += ProtoField(number, wire, numberValue = readVarint(input))
                1 -> {
                    val raw = readExactly(input, 8)
                    require(raw.size == 8) { "Truncated protobuf fixed64 value." }
                    fields += ProtoField(number, wire, bytesValue = raw)
                }
                2 -> {
                    val length = readVarint(input)
                    require(length in 0..MAX_FIELD_BYTES.toLong()) { "Invalid protobuf field length." }
                    val raw = readExactly(input, length.toInt())
                    require(raw.size == length.toInt()) { "Truncated protobuf bytes value." }
                    fields += ProtoField(number, wire, bytesValue = raw)
                }
                5 -> {
                    val raw = readExactly(input, 4)
                    require(raw.size == 4) { "Truncated protobuf fixed32 value." }
                    fields += ProtoField(number, wire, bytesValue = raw)
                }
                else -> error("Unsupported protobuf wire type $wire.")
            }
            require(fields.size <= MAX_FIELDS) { "Protobuf message has too many fields." }
        }
        return fields
    }

    fun firstBytes(fields: List<ProtoField>, number: Int): ByteArray? =
        fields.firstOrNull { it.number == number && it.wireType == 2 }?.bytesValue

    fun firstNumber(fields: List<ProtoField>, number: Int): Long? =
        fields.firstOrNull { it.number == number && it.wireType == 0 }?.numberValue

    private fun readExactly(input: ByteArrayInputStream, count: Int): ByteArray {
        val result = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(result, offset, count - offset)
            require(read > 0) { "Truncated protobuf field." }
            offset += read
        }
        return result
    }

    private fun readVarint(input: ByteArrayInputStream): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            val next = input.read()
            require(next >= 0) { "Truncated protobuf varint." }
            result = result or ((next and 0x7f).toLong() shl shift)
            if (next and 0x80 == 0) return result
            shift += 7
        }
        error("Malformed protobuf varint.")
    }

    private const val MAX_FIELDS = 4096
    private const val MAX_FIELD_BYTES = 1_048_576
}

internal object AndroidTvPairingSecret {
    fun calculate(
        code: String,
        clientKey: RSAPublicKey,
        serverKey: RSAPublicKey
    ): ByteArray {
        val normalized = code.trim().uppercase()
        require(normalized.matches(Regex("[0-9A-F]{6}"))) {
            "Enter the six-character hexadecimal code shown on the TV."
        }
        val nonce = hexToBytes(normalized.substring(2))
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(unsignedBytes(clientKey.modulus))
            update(unsignedBytes(clientKey.publicExponent))
            update(unsignedBytes(serverKey.modulus))
            update(unsignedBytes(serverKey.publicExponent))
            update(nonce)
        }.digest()
        val displayedCheckByte = normalized.substring(0, 2).toInt(16)
        require((digest[0].toInt() and 0xff) == displayedCheckByte) {
            "The code check did not match. Re-enter the code currently shown on the TV."
        }
        return digest
    }

    private fun unsignedBytes(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }

    private fun hexToBytes(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

/** Builds the RemoteImeBatchEdit payload used for text insertion on a focused TV field. */
internal object AndroidTvTextInputEncoder {
    fun encode(imeCounter: Int, fieldCounter: Int, text: String): ByteArray {
        require(text.isNotEmpty()) { "Enter text before sending it to the TV." }
        require(text.length <= MAX_TEXT_LENGTH) { "Text is too long to send to the TV." }
        require(imeCounter >= 0 && fieldCounter >= 0) { "Invalid TV text-field counters." }

        // The protocol offsets are character positions, not UTF-8 byte lengths.
        val cursor = text.codePointCount(0, text.length)
        val textField = ProtoWriter()
            .varint(1, cursor.toLong())
            .varint(2, cursor.toLong())
            .string(3, text)
            .toByteArray()
        val editInfo = ProtoWriter()
            .varint(1, 1)
            .bytes(2, textField)
            .toByteArray()

        return ProtoWriter()
            .varint(1, imeCounter.toLong())
            .varint(2, fieldCounter.toLong())
            .bytes(3, editInfo)
            .toByteArray()
    }

    private const val MAX_TEXT_LENGTH = 4_096
}

data class SavedAndroidTv(
    val name: String,
    val host: String,
    val certificateFingerprint: String
)

class AndroidTvRemoteClient(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    data class PairingChallenge internal constructor(
        val host: String,
        val displayName: String,
        internal val socket: SSLSocket,
        internal val input: DataInputStream,
        internal val output: DataOutputStream,
        internal val serverCertificate: X509Certificate
    ) : AutoCloseable {
        override fun close() {
            runCatching { socket.close() }
        }
    }

    fun loadSavedTv(): SavedAndroidTv? {
        val host = preferences.getString(KEY_HOST, null) ?: return null
        val fingerprint = preferences.getString(KEY_FINGERPRINT, null) ?: return null
        val name = preferences.getString(KEY_NAME, "Realme TechLife TV") ?: "Realme TechLife TV"
        return SavedAndroidTv(name, host, fingerprint)
    }

    suspend fun preparePairing(hostInput: String): PairingChallenge {
        val host = normalizeHost(hostInput)
        val identity = ensureClientIdentity()
        val socket = createTlsSocket(host, PAIRING_PORT, identity, null)
        try {
            val input = DataInputStream(socket.inputStream)
            val output = DataOutputStream(socket.outputStream)
            socket.soTimeout = PAIRING_TIMEOUT_MS

            sendPairingMessage(output, 10, ProtoWriter()
                .string(1, "atvremote")
                .string(2, "OneRemote")
                .toByteArray())
            expectPairingMessage(input, 11)

            val encoding = ProtoWriter().varint(1, 3).varint(2, 6).toByteArray()
            val options = ProtoWriter()
                .bytes(1, encoding)
                .varint(3, 1)
                .toByteArray()
            sendPairingMessage(output, 20, options)
            expectPairingMessage(input, 20)

            val configuration = ProtoWriter()
                .bytes(1, encoding)
                .varint(2, 1)
                .toByteArray()
            sendPairingMessage(output, 30, configuration)
            expectPairingMessage(input, 31)

            val serverCertificate = socket.session.peerCertificates.firstOrNull() as? X509Certificate
                ?: error("The TV did not present a valid TLS certificate.")
            socket.soTimeout = PAIRING_CODE_TIMEOUT_MS
            return PairingChallenge(
                host = host,
                displayName = "Realme TechLife TV",
                socket = socket,
                input = input,
                output = output,
                serverCertificate = serverCertificate
            )
        } catch (failure: Throwable) {
            runCatching { socket.close() }
            throw failure
        }
    }

    suspend fun finishPairing(challenge: PairingChallenge, code: String): SavedAndroidTv {
        val clientCertificate = clientCertificate()
        val clientKey = clientCertificate.publicKey as? RSAPublicKey
            ?: error("The phone's pairing key is not RSA.")
        val serverKey = challenge.serverCertificate.publicKey as? RSAPublicKey
            ?: error("The TV's pairing key is not RSA.")

        // Validate locally before consuming the live pairing socket. A mistyped check
        // byte can then be corrected without restarting the TV's pairing flow.
        val secret = AndroidTvPairingSecret.calculate(code, clientKey, serverKey)

        try {
            sendPairingMessage(challenge.output, 40, ProtoWriter().bytes(1, secret).toByteArray())
            expectPairingMessage(challenge.input, 41)

            val fingerprint = challenge.serverCertificate.fingerprint()
            val saved = SavedAndroidTv(challenge.displayName, challenge.host, fingerprint)
            preferences.edit()
                .putString(KEY_HOST, saved.host)
                .putString(KEY_NAME, saved.name)
                .putString(KEY_FINGERPRINT, saved.certificateFingerprint)
                .apply()
            return saved
        } finally {
            challenge.close()
        }
    }

    suspend fun connect(
        saved: SavedAndroidTv,
        onStatus: (String) -> Unit,
        onPowerState: (Boolean) -> Unit,
        onVolume: (Int, Int, Boolean) -> Unit,
        onTextInputState: (Boolean, String) -> Unit,
        onAppLinkSupport: (Boolean) -> Unit
    ): AndroidTvRemoteSession {
        val identity = ensureClientIdentity()
        val socket = createTlsSocket(
            normalizeHost(saved.host),
            REMOTE_PORT,
            identity,
            saved.certificateFingerprint
        )
        return AndroidTvRemoteSession(
            socket,
            onStatus,
            onPowerState,
            onVolume,
            onTextInputState,
            onAppLinkSupport
        ).also {
            it.start()
        }
    }

    fun forgetSavedTv() {
        preferences.edit().remove(KEY_HOST).remove(KEY_NAME).remove(KEY_FINGERPRINT).apply()
    }

    private fun sendPairingMessage(output: DataOutputStream, type: Int, payload: ByteArray) {
        val message = ProtoWriter()
            .varint(1, 2)
            .varint(2, STATUS_OK.toLong())
            .bytes(type, payload)
            .toByteArray()
        writeDelimited(output, message)
    }

    private fun expectPairingMessage(input: DataInputStream, expectedField: Int) {
        val bytes = readDelimited(input) ?: error("The TV closed the pairing connection.")
        val fields = ProtoReader.parse(bytes)
        val status = ProtoReader.firstNumber(fields, 2)?.toInt()
            ?: error("The TV sent an invalid pairing response.")
        if (status != STATUS_OK) {
            val reason = when (status) {
                400 -> "The TV rejected the pairing request."
                401 -> "The TV rejected the pairing configuration."
                402 -> "The TV rejected the pairing code."
                else -> "The TV returned pairing status $status."
            }
            error(reason)
        }
        if (ProtoReader.firstBytes(fields, expectedField) == null) {
            error("The TV returned an unexpected pairing response (expected message $expectedField).")
        }
    }

    private fun ensureClientIdentity(): KeyStore {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!store.containsAlias(CLIENT_KEY_ALIAS)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
            val now = System.currentTimeMillis()
            val subject = javax.security.auth.x500.X500Principal(
                "CN=OneRemote-" + UUID.randomUUID().toString()
            )
            val specification = KeyGenParameterSpec.Builder(
                CLIENT_KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(subject)
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(Date(now - 60_000L))
                .setCertificateNotAfter(Date(now + CERTIFICATE_LIFETIME_MS))
                .build()
            generator.initialize(specification)
            generator.generateKeyPair()
        }
        return KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    private fun clientCertificate(): X509Certificate {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return store.getCertificate(CLIENT_KEY_ALIAS) as? X509Certificate
            ?: error("The phone's pairing certificate is unavailable.")
    }

    private fun createTlsSocket(
        host: String,
        port: Int,
        keyStore: KeyStore,
        expectedFingerprint: String?
    ): SSLSocket {
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, null)
        }.keyManagers
        val trustManager = if (expectedFingerprint == null) {
            TrustAnyTvCertificate()
        } else {
            PinnedTvCertificate(expectedFingerprint)
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(keyManagers, arrayOf(trustManager), SecureRandom())
        }
        val socket = sslContext.socketFactory.createSocket() as SSLSocket
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = CONNECT_TIMEOUT_MS
            val supported = socket.supportedProtocols.toSet()
            socket.enabledProtocols = listOf("TLSv1.3", "TLSv1.2").filter { it in supported }.toTypedArray()
            require(socket.enabledProtocols.isNotEmpty()) { "This phone does not support a compatible TLS version." }
            socket.startHandshake()
            return socket
        } catch (failure: Throwable) {
            runCatching { socket.close() }
            throw failure
        }
    }

    private fun normalizeHost(input: String): String {
        val host = input.trim().removePrefix("http://").removePrefix("https://")
            .substringBefore('/').substringBefore(':')
        require(host.isNotBlank() && host.matches(Regex("[A-Za-z0-9.\\-]+"))) {
            "Enter the TV's local IP address or host name."
        }
        return host
    }

    private class TrustAnyTvCertificate : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain.isNullOrEmpty()) throw CertificateException("The TV did not provide a certificate.")
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private class PinnedTvCertificate(private val expectedFingerprint: String) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val certificate = chain?.firstOrNull() ?: throw CertificateException("The TV certificate is missing.")
            val actual = certificate.fingerprint()
            if (!actual.equals(expectedFingerprint, ignoreCase = true)) {
                throw CertificateException("The TV certificate changed. Remove this saved TV and pair it again.")
            }
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        const val PREFERENCES = "android_tv_remote"
        const val KEY_HOST = "host"
        const val KEY_NAME = "name"
        const val KEY_FINGERPRINT = "certificate_fingerprint"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val CLIENT_KEY_ALIAS = "oneremote_android_tv_client"
        const val STATUS_OK = 200
        const val PAIRING_PORT = 6467
        const val REMOTE_PORT = 6466
        const val CONNECT_TIMEOUT_MS = 8_000
        const val PAIRING_TIMEOUT_MS = 12_000
        const val PAIRING_CODE_TIMEOUT_MS = 120_000
        const val CERTIFICATE_LIFETIME_MS = 10L * 365 * 24 * 60 * 60 * 1_000
    }
}

class AndroidTvRemoteSession internal constructor(
    private val socket: SSLSocket,
    private val onStatus: (String) -> Unit,
    private val onPowerState: (Boolean) -> Unit,
    private val onVolume: (Int, Int, Boolean) -> Unit,
    private val onTextInputState: (Boolean, String) -> Unit,
    private val onAppLinkSupport: (Boolean) -> Unit
) : AutoCloseable {
    @Volatile private var ready = false
    @Volatile private var closed = false
    @Volatile private var negotiatedFeatures = 0
    private val imeLock = Any()
    private var imeCounter = 0
    private var imeFieldCounter = 0
    private var hasImeFieldCounter = false
    private var textInputLabel = "TV text input"
    private val output = DataOutputStream(socket.outputStream)
    private val writeLock = Any()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    internal fun start() {
        Thread({
            var disconnectReason = "The TV disconnected."
            try {
                val input = DataInputStream(socket.inputStream)
                socket.soTimeout = 15_000
                while (!closed) {
                    val bytes = try {
                        readDelimited(input)
                    } catch (_: java.net.SocketTimeoutException) {
                        continue
                    } ?: break
                    handleRemoteMessage(bytes)
                }
            } catch (failure: Throwable) {
                if (!closed) {
                    disconnectReason = failure.message ?: "The TV connection was lost."
                }
            } finally {
                ready = false
                if (!closed) {
                    postStatus("$disconnectReason Connection disconnected. Tap Connect to retry.")
                }
                runCatching { socket.close() }
            }
        }, "OneRemote-AndroidTV").apply {
            isDaemon = true
            start()
        }
    }

    fun sendKey(keyCode: Int) {
        check(!closed && ready) { "The TV remote session is not ready." }
        require(keyCode in 1..304) { "Unsupported Android TV key code." }
        val keyInject = ProtoWriter().varint(1, keyCode.toLong()).varint(2, 3).toByteArray()
        sendRemoteMessage(10, keyInject)
    }

    fun launchApp(shortcut: AndroidTvAppShortcut) {
        check(!closed && ready) { "The TV remote session is not ready." }
        check(negotiatedFeatures and FEATURE_APP_LINK != 0) {
            "This TV remote service does not support app-link launch."
        }
        sendRemoteMessage(90, AndroidTvAppLinkLaunchEncoder.encode(shortcut))
    }

    fun sendText(text: String) {
        check(!closed && ready) { "The TV remote session is not ready." }
        check(negotiatedFeatures and FEATURE_IME != 0) {
            "This TV remote service does not advertise text-input support."
        }
        val counters = synchronized(imeLock) {
            check(hasImeFieldCounter) {
                "Open a text field on the TV first, such as Search, then try sending text."
            }
            imeCounter to imeFieldCounter
        }
        sendRemoteMessage(21, AndroidTvTextInputEncoder.encode(counters.first, counters.second, text))
    }

    override fun close() {
        closed = true
        ready = false
        runCatching { socket.close() }
    }

    private fun handleRemoteMessage(bytes: ByteArray) {
        val fields = ProtoReader.parse(bytes)
        val configure = ProtoReader.firstBytes(fields, 1)
        if (configure != null) {
            val configFields = ProtoReader.parse(configure)
            val supportedFeatures = ProtoReader.firstNumber(configFields, 1)?.toInt() ?: 0
            negotiatedFeatures = supportedFeatures and FEATURE_SUPPORTED
            mainHandler.post {
                onAppLinkSupport(negotiatedFeatures and FEATURE_APP_LINK != 0)
            }
            if (negotiatedFeatures and FEATURE_KEY == 0) {
                error("This TV remote service does not offer key control.")
            }
            val deviceInfo = ProtoWriter()
                .varint(3, 1)
                .string(4, "1")
                .string(5, "atvremote")
                .string(6, "0.1.0")
                .toByteArray()
            val reply = ProtoWriter()
                .varint(1, negotiatedFeatures.toLong())
                .bytes(2, deviceInfo)
                .toByteArray()
            sendRemoteMessage(1, reply)
            return
        }

        val setActive = ProtoReader.firstBytes(fields, 2)
        if (setActive != null) {
            sendRemoteMessage(2, ProtoWriter().varint(1, negotiatedFeatures.toLong()).toByteArray())
            postStatus("TV accepted the remote features. Waiting for the ready signal…")
            return
        }

        val ping = ProtoReader.firstBytes(fields, 8)
        if (ping != null) {
            val pingValue = ProtoReader.firstNumber(ProtoReader.parse(ping), 1) ?: 0L
            sendRemoteMessage(9, ProtoWriter().varint(1, pingValue).toByteArray())
            return
        }

        val started = ProtoReader.firstBytes(fields, 40)
        if (started != null) {
            val isOn = ProtoReader.firstNumber(ProtoReader.parse(started), 1) == 1L
            // This message confirms that the remote service has started. The payload
            // separately reports TV power state; standby must not disable Wake/Power keys.
            ready = true
            mainHandler.post {
                onPowerState(isOn)
                onStatus("Connected to Android TV over your local Wi-Fi.")
            }
            return
        }

        val imeKeyInject = ProtoReader.firstBytes(fields, 20)
        if (imeKeyInject != null) {
            val imeFields = ProtoReader.parse(imeKeyInject)
            val appInfoBytes = ProtoReader.firstBytes(imeFields, 1)
            val fieldStatusBytes = ProtoReader.firstBytes(imeFields, 2)
            if (appInfoBytes != null && fieldStatusBytes != null) {
                val appInfo = ProtoReader.parse(appInfoBytes)
                val fieldStatus = ProtoReader.parse(fieldStatusBytes)
                val imeValue = ProtoReader.firstNumber(appInfo, 1)?.toInt() ?: 0
                val fieldValue = ProtoReader.firstNumber(fieldStatus, 1)?.toInt() ?: 0
                val label = ProtoReader.firstBytes(fieldStatus, 6)
                    ?.toString(Charsets.UTF_8)?.take(80)?.takeIf { it.isNotBlank() }
                // Proto3 omits default numeric zero values. The presence of this
                // focused-field notification is sufficient to treat zero as valid.
                updateImeState(imeValue, fieldValue, label)
            }
            return
        }

        val imeShowRequest = ProtoReader.firstBytes(fields, 22)
        if (imeShowRequest != null) {
            val requestFields = ProtoReader.parse(imeShowRequest)
            val fieldStatusBytes = ProtoReader.firstBytes(requestFields, 2)
            if (fieldStatusBytes != null) {
                val fieldStatus = ProtoReader.parse(fieldStatusBytes)
                val fieldValue = ProtoReader.firstNumber(fieldStatus, 1)?.toInt() ?: 0
                val label = ProtoReader.firstBytes(fieldStatus, 6)
                    ?.toString(Charsets.UTF_8)?.take(80)?.takeIf { it.isNotBlank() }
                updateImeState(fieldCounter = fieldValue, label = label)
            }
            return
        }

        val imeBatchEdit = ProtoReader.firstBytes(fields, 21)
        if (imeBatchEdit != null) {
            val batchFields = ProtoReader.parse(imeBatchEdit)
            val imeValue = ProtoReader.firstNumber(batchFields, 1)?.toInt()
            val fieldValue = ProtoReader.firstNumber(batchFields, 2)?.toInt()
            updateImeState(imeValue, fieldValue, null)
            return
        }

        val volume = ProtoReader.firstBytes(fields, 50)
        if (volume != null) {
            val values = ProtoReader.parse(volume)
            val max = ProtoReader.firstNumber(values, 6)?.toInt() ?: 0
            val level = ProtoReader.firstNumber(values, 7)?.toInt() ?: 0
            val muted = ProtoReader.firstNumber(values, 8) == 1L
            mainHandler.post { onVolume(level, max, muted) }
        }

        val remoteError = ProtoReader.firstBytes(fields, 3)
        if (remoteError != null) {
            postStatus("The TV reported a remote-service error.")
            error("The TV rejected the remote session.")
        }
    }

    private fun sendRemoteMessage(field: Int, payload: ByteArray) {
        synchronized(writeLock) {
            check(!closed) { "The TV connection is closed." }
            writeDelimited(output, ProtoWriter().bytes(field, payload).toByteArray())
        }
    }

    private fun updateImeState(
        imeValue: Int? = null,
        fieldCounter: Int? = null,
        label: String? = null
    ) {
        val available: Boolean
        val currentLabel: String
        synchronized(imeLock) {
            if (imeValue != null) imeCounter = imeValue
            if (fieldCounter != null) {
                imeFieldCounter = fieldCounter
                hasImeFieldCounter = true
            }
            if (label != null) textInputLabel = label
            available = hasImeFieldCounter && negotiatedFeatures and FEATURE_IME != 0
            currentLabel = textInputLabel
        }
        mainHandler.post { onTextInputState(available, currentLabel) }
    }

    private fun postStatus(message: String) {
        mainHandler.post { onStatus(message) }
    }

    private companion object {
        const val FEATURE_PING = 1
        const val FEATURE_KEY = 2
        const val FEATURE_IME = 4
        const val FEATURE_POWER = 32
        const val FEATURE_VOLUME = 64
        const val FEATURE_APP_LINK = 512
        const val FEATURE_KEY_PING_POWER_VOLUME =
            FEATURE_PING or FEATURE_KEY or FEATURE_IME or FEATURE_POWER or FEATURE_VOLUME
        const val FEATURE_SUPPORTED = FEATURE_KEY_PING_POWER_VOLUME or FEATURE_APP_LINK
    }
}

private fun writeDelimited(output: DataOutputStream, message: ByteArray) {
    require(message.size <= 1_048_576) { "TV remote message is too large." }
    var value = message.size
    while ((value and 0x7f.inv()) != 0) {
        output.writeByte((value and 0x7f) or 0x80)
        value = value ushr 7
    }
    output.writeByte(value)
    output.write(message)
    output.flush()
}

private fun readDelimited(input: DataInputStream): ByteArray? {
    val first = input.read()
    if (first < 0) return null
    var length = 0
    var shift = 0
    var next = first
    while (true) {
        length = length or ((next and 0x7f) shl shift)
        if (next and 0x80 == 0) break
        shift += 7
        require(shift < 32) { "Invalid TV remote message length." }
        next = input.read()
        require(next >= 0) { "TV connection ended inside a message length." }
    }
    require(length in 1..1_048_576) { "Invalid TV remote message size." }
    val message = ByteArray(length)
    input.readFully(message)
    return message
}

private fun X509Certificate.fingerprint(): String =
    MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }
