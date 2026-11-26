package dev.localremote.app

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.localremote.app.connectivity.androidtv.AndroidTvDiscovery
import dev.localremote.app.connectivity.androidtv.AndroidTvEndpoint
import dev.localremote.app.connectivity.androidtv.AndroidTvRemoteClient
import dev.localremote.app.connectivity.androidtv.AndroidTvRemoteSession
import dev.localremote.app.connectivity.androidtv.SavedAndroidTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RealmeTvRemoteScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(context) { AndroidTvRemoteClient(context) }
    val discovery = remember(context) { AndroidTvDiscovery(context) }

    var discovered by remember { mutableStateOf<List<AndroidTvEndpoint>>(emptyList()) }
    var savedTv by remember { mutableStateOf(client.loadSavedTv()) }
    var hostInput by rememberSaveable { mutableStateOf(savedTv?.host.orEmpty()) }
    var challenge by remember { mutableStateOf<AndroidTvRemoteClient.PairingChallenge?>(null) }
    var pairingCode by rememberSaveable { mutableStateOf("") }
    var showPairingDialog by remember { mutableStateOf(false) }
    var pairingBusy by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<AndroidTvRemoteSession?>(null) }
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Find your TV on the same Wi-Fi network.") }
    var powerState by remember { mutableStateOf<String?>(null) }
    var volumeState by remember { mutableStateOf<String?>(null) }
    var textInputAvailable by remember { mutableStateOf(false) }
    var textInputHint by remember { mutableStateOf("TV text input") }
    var textToSend by rememberSaveable { mutableStateOf("") }
    var selectedMode by rememberSaveable { mutableStateOf("Remote") }
    var showConnectionSetup by rememberSaveable { mutableStateOf(savedTv == null) }
    val swipeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    val voiceTypingLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) textToSend = spokenText
        }
    }

    DisposableEffect(discovery) {
        discovery.start(
            onFound = { endpoint ->
                discovered = (discovered.filterNot { it.host == endpoint.host } + endpoint)
                    .sortedBy { it.name.lowercase() }
            },
            onError = { error -> status = error }
        )
        onDispose { discovery.close() }
    }

    DisposableEffect(session) {
        val activeSession = session
        onDispose {
            activeSession?.close()
        }
    }

    fun updateConnectionStatus(message: String) {
        status = message
        if (message.startsWith("Connected to Android TV")) {
            connected = true
        } else if (
            message.contains("disconnected", ignoreCase = true) ||
            message.contains("connection was lost", ignoreCase = true) ||
            message.contains("certificate changed", ignoreCase = true)
        ) {
            connected = false
            textInputAvailable = false
        }
    }

    fun connectToSaved(tv: SavedAndroidTv) {
        if (connecting) return
        connecting = true
        connected = false
        textInputAvailable = false
        textInputHint = "TV text input"
        status = "Connecting securely to ${tv.host}…"
        scope.launch {
            try {
                val nextSession = withContext(Dispatchers.IO) {
                    client.connect(
                        tv,
                        onStatus = ::updateConnectionStatus,
                        onPowerState = { isOn ->
                            powerState = if (isOn) "TV reported ON" else "TV reported STANDBY/OFF"
                        },
                        onVolume = { level, max, muted ->
                            volumeState = if (max > 0) {
                                "Volume $level/$max${if (muted) " · Muted" else ""}"
                            } else {
                                if (muted) "Muted" else "Volume information received"
                            }
                        },
                        onTextInputState = { available, label ->
                            textInputAvailable = available
                            textInputHint = label
                        }
                    )
                }
                session?.close()
                session = nextSession
                if (!connected) {
                    status = "Secure TLS session opened. Waiting for the TV ready signal…"
                }
            } catch (failure: Throwable) {
                updateConnectionStatus(
                    failure.message ?: "Could not connect. Check the TV is awake and on the same Wi-Fi."
                )
            } finally {
                connecting = false
            }
        }
    }

    fun beginPairing() {
        val host = hostInput.trim()
        if (host.isBlank() || pairingBusy || connecting) {
            status = "Select a discovered TV or enter its local IP address."
            return
        }
        pairingBusy = true
        status = "Connecting to the TV pairing service…"
        scope.launch {
            try {
                val pending = withContext(Dispatchers.IO) { client.preparePairing(host) }
                challenge?.close()
                challenge = pending
                pairingCode = ""
                showPairingDialog = true
                status = "The TV should now display a six-character pairing code."
            } catch (failure: Throwable) {
                status = failure.message
                    ?: "Pairing could not start. Check Android TV Remote Service on the TV."
            } finally {
                pairingBusy = false
            }
        }
    }

    fun finishPairing() {
        val pending = challenge ?: return
        val code = pairingCode.trim().uppercase()
        if (!code.matches(Regex("[0-9A-F]{6}")) || pairingBusy) return
        pairingBusy = true
        status = "Verifying the code and saving this TV securely…"
        scope.launch {
            try {
                val tv = withContext(Dispatchers.IO) {
                    client.finishPairing(pending, code)
                }
                challenge = null
                showPairingDialog = false
                savedTv = tv
                hostInput = tv.host
                pairingCode = ""
                status = "TV paired. Opening the remote connection…"
                showConnectionSetup = false
                connectToSaved(tv)
            } catch (failure: Throwable) {
                val message = failure.message.orEmpty()
                if (message.contains("code check did not match", ignoreCase = true)) {
                    // The local checksum rejected the entry before the pairing socket was
                    // consumed, so keep the active challenge and let the user retry.
                    pairingCode = ""
                    status = "That code does not match this TV challenge. Re-enter the six-character code shown on the TV."
                } else {
                    pending.close()
                    challenge = null
                    showPairingDialog = false
                    status = failure.message ?: "Pairing failed. Start pairing again on the TV."
                }
            } finally {
                pairingBusy = false
            }
        }
    }

    fun sendKey(code: Int) {
        val active = session
        if (!connected || active == null) {
            status = "Connect to the TV before sending remote commands."
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { active.sendKey(code) }
            }.onFailure { failure ->
                updateConnectionStatus(failure.message ?: "The TV did not accept this key.")
            }
        }
    }

    fun sendText() {
        val active = session
        val value = textToSend
        if (!connected || !textInputAvailable || active == null) {
            status = "Open a text field on the TV before sending text."
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { active.sendText(value) }
            }.onSuccess {
                textToSend = ""
                status = "Text sent to the active TV text field."
            }.onFailure { failure ->
                status = failure.message ?: "Text could not be sent to the TV."
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Realme TechLife TV",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Direct Android TV remote over your home Wi-Fi. No IR blaster, ADB, root or cloud relay is used for this connection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (savedTv == null) "Connect your TV" else "TV connection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (savedTv == null) "Pair once, then reconnect securely" else "Saved TV · " + savedTv!!.host,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showConnectionSetup = !showConnectionSetup }) {
                            Text(if (showConnectionSetup) "Hide" else "Manage")
                        }
                    }
                    if (showConnectionSetup) {
                        Text(
                            "Keep the phone and TV on the same home Wi-Fi. OneRemote looks only for the Android TV Remote service; it does not scan other network ports.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    discovered = emptyList()
                                    discovery.start(
                                        onFound = { endpoint ->
                                            discovered = (discovered.filterNot { it.host == endpoint.host } + endpoint)
                                                .sortedBy { it.name.lowercase() }
                                        },
                                        onError = { error -> status = error }
                                    )
                                    status = "Searching for Android TV Remote devices on this Wi-Fi…"
                                }
                            ) { Text("Scan Wi-Fi") }
                            if (discovered.isNotEmpty()) {
                                Text(
                                    discovered.size.toString() + " TV service(s) found",
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        discovered.forEach { endpoint ->
                            OutlinedButton(
                                onClick = { hostInput = endpoint.host },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(endpoint.name + "  ·  " + endpoint.host + ":" + endpoint.port)
                            }
                        }
                        OutlinedTextField(
                            value = hostInput,
                            onValueChange = { hostInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("TV IP address") },
                            placeholder = { Text("e.g. 192.168.1.25") }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = ::beginPairing,
                                enabled = hostInput.isNotBlank() && !pairingBusy && !connecting
                            ) { Text(if (pairingBusy) "Working…" else "Pair this TV") }
                            if (savedTv != null) {
                                OutlinedButton(
                                    onClick = { connectToSaved(savedTv!!) },
                                    enabled = !pairingBusy && !connecting
                                ) { Text(if (connecting) "Connecting…" else "Reconnect") }
                            }
                        }
                    }
                }
            }
        }

        if (savedTv != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(savedTv!!.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Paired · ${savedTv!!.host}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        session?.close()
                        session = null
                        client.forgetSavedTv()
                        savedTv = null
                        connected = false
                        powerState = null
                        volumeState = null
                        textInputAvailable = false
                        textInputHint = "TV text input"
                        textToSend = ""
                        showConnectionSetup = true
                        status = "Saved TV pairing removed from this phone."
                    }) { Text("Forget") }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (connected) "Connected over Wi-Fi" else "Connection status",
                        fontWeight = FontWeight.SemiBold,
                        color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                    powerState?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    volumeState?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        item {
            val modes = listOf("Remote", "Touchpad", "Keyboard", "More")
            TabRow(selectedTabIndex = modes.indexOf(selectedMode).coerceAtLeast(0)) {
                modes.forEach { mode ->
                    Tab(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                        text = { Text(mode) }
                    )
                }
            }
        }

        if (selectedMode == "Remote") {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Quick controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvKeyButton("Power", 26, connected, ::sendKey, Modifier.weight(1f))
                        TvKeyButton("TV Power", 177, connected, ::sendKey, Modifier.weight(1f))
                        TvKeyButton("Input", 178, connected, ::sendKey, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvKeyButton("Back", 4, connected, ::sendKey, Modifier.weight(1f))
                        TvKeyButton("Home", 3, connected, ::sendKey, Modifier.weight(1f))
                        TvKeyButton("Menu", 82, connected, ::sendKey, Modifier.weight(1f))
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Navigation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        TvKeyButton("▲", 19, connected, ::sendKey, Modifier.fillMaxWidth(0.32f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvKeyButton("◀", 21, connected, ::sendKey, Modifier.weight(1f))
                            TvKeyButton("OK", 23, connected, ::sendKey, Modifier.weight(1.2f))
                            TvKeyButton("▶", 22, connected, ::sendKey, Modifier.weight(1f))
                        }
                        TvKeyButton("▼", 20, connected, ::sendKey, Modifier.fillMaxWidth(0.32f))
                        Text(
                            "Tap a direction, then OK to select",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvKeyButton("Vol −", 25, connected, ::sendKey, Modifier.weight(1f))
                    TvKeyButton("Mute", 164, connected, ::sendKey, Modifier.weight(1f))
                    TvKeyButton("Vol +", 24, connected, ::sendKey, Modifier.weight(1f))
                }
            }
        }

        if (selectedMode == "Touchpad") {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Swipe navigation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Swipe up, down, left or right to send one D-pad press. Tap the pad to select. This uses Android TV key events, not mouse-pointer control.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(270.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(28.dp))
                                .clickable(enabled = connected) { sendKey(23) }
                                .pointerInput(connected, swipeThresholdPx) {
                                    var dragX = 0f
                                    var dragY = 0f
                                    detectDragGestures(
                                        onDragStart = {
                                            dragX = 0f
                                            dragY = 0f
                                        },
                                        onDragEnd = {
                                            val keyCode = TvGestureMapper.toKeyCode(dragX, dragY, swipeThresholdPx)
                                            if (keyCode != null) sendKey(keyCode)
                                            dragX = 0f
                                            dragY = 0f
                                        },
                                        onDragCancel = {
                                            dragX = 0f
                                            dragY = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragX += amount.x
                                            dragY += amount.y
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("↑", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    Text("←", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("TAP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("→", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("↓", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            if (connected) "Ready for gestures" else "Connect the TV to enable gestures",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvKeyButton("Back", 4, connected, ::sendKey, Modifier.weight(1f))
                    TvKeyButton("Home", 3, connected, ::sendKey, Modifier.weight(1f))
                    TvKeyButton("Vol +", 24, connected, ::sendKey, Modifier.weight(1f))
                }
            }
        }

        if (selectedMode == "Keyboard") {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Phone keyboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (textInputAvailable) {
                                "The TV reports an active text field. Type or dictate on the phone, then send."
                            } else {
                                "Open Search or another text field on the TV. OneRemote enables TV text entry only when that field is reported by the TV."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = textToSend,
                            onValueChange = { textToSend = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = connected && textInputAvailable,
                            label = { Text(textInputHint) },
                            placeholder = { Text("Type a search or message") },
                            minLines = 3,
                            maxLines = 6
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = ::sendText,
                                enabled = connected && textInputAvailable && textToSend.isNotBlank()
                            ) { Text("Send to TV") }
                            OutlinedButton(onClick = { textToSend = "" }) { Text("Clear") }
                        }
                        OutlinedButton(
                            onClick = {
                                val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak text to enter on your TV")
                                }
                                runCatching { voiceTypingLauncher.launch(voiceIntent) }
                                    .onFailure { status = "Voice typing is not available on this phone." }
                            },
                            enabled = connected
                        ) {
                            Text("Voice typing")
                        }
                        Text(
                            "Voice typing transcribes on your phone first. It does not activate the TV assistant.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (selectedMode == "More") {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Volume and channels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvKeyButton("Vol −", 25, connected, ::sendKey, Modifier.weight(1f))
                        TvKeyButton("Mute", 164, connected, ::sendKey, Modifier.weight(1f))
                        TvKeyButton("Vol +", 24, connected, ::sendKey, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvKeyButton("CH −", 167, connected, ::sendKey, Modifier.weight(1f))
                        TvKeyButton("Guide", 172, connected, ::sendKey, Modifier.weight(1f))
                        TvKeyButton("CH +", 166, connected, ::sendKey, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvKeyButton("Info", 165, connected, ::sendKey, Modifier.weight(1f))
                        TvKeyButton("Settings", 176, connected, ::sendKey, Modifier.weight(1f))
                        TvKeyButton("TV", 170, connected, ::sendKey, Modifier.weight(1f))
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Number pad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    listOf(
                        listOf("1" to 8, "2" to 9, "3" to 10),
                        listOf("4" to 11, "5" to 12, "6" to 13),
                        listOf("7" to 14, "8" to 15, "9" to 16),
                        listOf("0" to 7)
                    ).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (label, code) ->
                                TvKeyButton(label, code, connected, ::sendKey, Modifier.weight(1f))
                            }
                            if (row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Media", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvKeyButton("⏮", 89, connected, ::sendKey, Modifier.weight(1f))
                        TvKeyButton("Play / Pause", 85, connected, ::sendKey, Modifier.weight(1f))
                        TvKeyButton("Stop", 86, connected, ::sendKey, Modifier.weight(1f))
                        TvKeyButton("⏭", 90, connected, ::sendKey, Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Text(
                "Compatibility note: the TV must have Android TV Remote Service enabled and expose the Android TV Remote v2 service. This build can be paired only after your actual TV completes the on-screen code handshake. Model-specific physical validation is still pending.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPairingDialog && challenge != null) {
        AlertDialog(
            onDismissRequest = {
                challenge?.close()
                challenge = null
                showPairingDialog = false
            },
            title = { Text("Enter the code shown on your TV") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Look for the six-character hexadecimal pairing code on the TV screen.")
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { value ->
                            pairingCode = value.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        },
                        singleLine = true,
                        label = { Text("6-character TV code") },
                        placeholder = { Text("A1B2C3") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = ::finishPairing,
                    enabled = pairingCode.length == 6 && !pairingBusy
                ) { Text(if (pairingBusy) "Verifying…" else "Pair") }
            },
            dismissButton = {
                TextButton(onClick = {
                    challenge?.close()
                    challenge = null
                    showPairingDialog = false
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TvKeyButton(
    label: String,
    keyCode: Int,
    connected: Boolean,
    onSend: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { onSend(keyCode) },
        enabled = connected,
        modifier = modifier.height(52.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
