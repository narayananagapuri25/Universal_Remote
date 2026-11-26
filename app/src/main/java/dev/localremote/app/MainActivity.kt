package dev.localremote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.localremote.app.connectivity.infrared.IrHardwareStatus
import dev.localremote.app.data.local.SavedDeviceEntity
import dev.localremote.app.platform.infrared.AndroidConsumerIrDriver
import dev.localremote.app.platform.infrared.BuiltInIrTransmitter

private enum class Destination(val label: String) {
    DEVICES("Devices"),
    DISCOVER("Discover"),
    SETTINGS("Settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = (application as OneRemoteApplication).database

        setContent {
            OneRemoteTheme {
                val savedDevices by database.savedDeviceDao()
                    .observeAll()
                    .collectAsStateWithLifecycle(initialValue = emptyList())

                val irTransmitter = remember {
                    BuiltInIrTransmitter(AndroidConsumerIrDriver(applicationContext))
                }
                val irHardware by produceState<IrHardwareStatus?>(
                    initialValue = null,
                    key1 = irTransmitter
                ) {
                    value = irTransmitter.inspect()
                }

                var destination by rememberSaveable {
                    mutableStateOf(Destination.DEVICES)
                }

                OneRemoteApp(
                    destination = destination,
                    savedDevices = savedDevices,
                    irHardware = irHardware,
                    onDestinationSelected = { destination = it }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneRemoteApp(
    destination: Destination,
    savedDevices: List<SavedDeviceEntity>,
    irHardware: IrHardwareStatus?,
    onDestinationSelected: (Destination) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("OneRemote", fontWeight = FontWeight.SemiBold)
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { onDestinationSelected(item) },
                        icon = { Text(item.label.take(1)) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { contentPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (destination) {
                Destination.DEVICES -> DevicesScreen(savedDevices)
                Destination.DISCOVER -> DiscoverScreen(irHardware)
                Destination.SETTINGS -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun DevicesScreen(devices: List<SavedDeviceEntity>) {
    if (devices.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "⌁",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Your remotes will appear here",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Saved devices stay on this phone. Configured remotes will work offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "Discover a compatible TV, AC or set-top box to get started.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Your devices",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(devices, key = { it.id }) { device ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            device.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listOfNotNull(device.manufacturer, device.model)
                                .joinToString(" · ")
                                .ifBlank { device.category },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Saved profile · ${device.connectionState.replace('_', ' ').lowercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverScreen(irHardware: IrHardwareStatus?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Discover devices",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Built-in infrared hardware is checked on this phone. Network and Bluetooth discovery are still under development.",
            style = MaterialTheme.typography.bodyLarge
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Built-in IR transmitter", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                when {
                    irHardware == null -> Text("Checking phone hardware…")
                    !irHardware.transmitterAvailable -> Text(
                        irHardware.diagnostic ?: "No built-in IR transmitter is available."
                    )
                    irHardware.supportedCarrierRanges.isEmpty() -> Text(
                        irHardware.diagnostic
                            ?: "IR hardware detected, but carrier-frequency support could not be verified."
                    )
                    else -> {
                        Text("Transmitter detected")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            irHardware.supportedCarrierRanges.joinToString { range ->
                                "${range.minHz / 1_000f}–${range.maxHz / 1_000f} kHz"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("First-release categories", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Televisions")
                Text("Air conditioners")
                Text("Set-top boxes")
            }
        }
        Text(
            "The IR transmission engine now validates patterns and carrier frequencies before using Android's built-in transmitter. No appliance command is sent during hardware inspection. IR learning still requires a compatible external receiver.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        SettingRow("Storage", "Saved device profiles are stored locally.")
        SettingRow("Internet", "Not required for configured local remotes.")
        SettingRow("Account", "No account or sign-in.")
        SettingRow("Privacy", "No analytics or behavioural tracking is included.")
        SettingRow("Build", "Development foundation · 0.1.0")
        Spacer(Modifier.height(8.dp))
        Text(
            "Branding, package ID and release signing remain provisional until identity and release checks are complete.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingRow(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
