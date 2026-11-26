package dev.localremote.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.localremote.app.connectivity.infrared.IrHardwareStatus
import dev.localremote.app.connectivity.infrared.IrTransmitResult
import dev.localremote.app.connectivity.infrared.importexport.FlipperIrFileCodec
import dev.localremote.app.connectivity.infrared.protocol.IrRemoteProfile
import dev.localremote.app.connectivity.infrared.protocol.IrRemoteButton
import dev.localremote.app.connectivity.infrared.protocol.RemoteControlMapping
import dev.localremote.app.connectivity.infrared.protocol.RemoteControlRole
import dev.localremote.app.core.model.DeviceCategory
import dev.localremote.app.data.local.SavedDeviceEntity
import dev.localremote.app.data.local.DaikinAcProfileDao
import dev.localremote.app.data.local.IrRemoteProfileDao
import dev.localremote.app.data.local.RemoteControlMappingMapper
import dev.localremote.app.data.local.DaikinAcProfileEntity
import dev.localremote.app.data.local.SavedIrProfileRepository
import dev.localremote.app.platform.infrared.AndroidConsumerIrDriver
import dev.localremote.app.platform.infrared.BuiltInIrTransmitter
import dev.localremote.app.connectivity.infrared.protocol.ProtocolCommandDispatcher
import dev.localremote.app.connectivity.infrared.protocol.ac.DaikinAcCommandDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

private enum class Destination(val label: String, val icon: Int) {
    DEVICES("Devices", R.drawable.ic_nav_devices),
    DISCOVER("Discover", R.drawable.ic_nav_discover),
    TV_REMOTE("TV", R.drawable.ic_nav_tv),
    SETTINGS("Settings", R.drawable.ic_nav_settings)
}

private sealed interface ImportState {
    data object Idle : ImportState
    data object Reading : ImportState
    data class Preview(val profile: IrRemoteProfile, val sourceName: String) : ImportState
    data class Error(val message: String) : ImportState
    data class Saved(val displayName: String) : ImportState
}

private sealed interface RemoteLoadState {
    data object Loading : RemoteLoadState
    data class Loaded(val profile: IrRemoteProfile) : RemoteLoadState
    data class Error(val message: String) : RemoteLoadState
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as OneRemoteApplication

        setContent {
            OneRemoteTheme {
                val savedDevices by app.database.savedDeviceDao()
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

                OneRemoteApp(
                    savedDevices = savedDevices,
                    irHardware = irHardware,
                    profileRepository = app.irProfileRepository,
                    dispatcher = remember(irTransmitter) {
                        ProtocolCommandDispatcher(irTransmitter)
                    },
                    daikinDispatcher = remember(irTransmitter) {
                        DaikinAcCommandDispatcher(irTransmitter)
                    },
                    daikinProfileDao = app.database.daikinAcProfileDao(),
                    irProfileDao = app.database.irRemoteProfileDao()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneRemoteApp(
    savedDevices: List<SavedDeviceEntity>,
    irHardware: IrHardwareStatus?,
    profileRepository: SavedIrProfileRepository,
    dispatcher: ProtocolCommandDispatcher,
    daikinDispatcher: DaikinAcCommandDispatcher,
    daikinProfileDao: DaikinAcProfileDao,
    irProfileDao: IrRemoteProfileDao
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedDaikinProfiles by daikinProfileDao.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var destination by rememberSaveable { mutableStateOf(Destination.DEVICES) }
    var openProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var openDaikinAc by rememberSaveable { mutableStateOf(false) }
    var selectedDaikinProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var importState by remember { mutableStateOf<ImportState>(ImportState.Idle) }
    var importDisplayName by rememberSaveable { mutableStateOf("Imported remote") }
    var importBrand by rememberSaveable { mutableStateOf("Custom") }
    var importModel by rememberSaveable { mutableStateOf("") }
    var importCategory by rememberSaveable { mutableStateOf(DeviceCategory.TELEVISION) }
    var importBusy by remember { mutableStateOf(false) }
    var currentRemoteState by remember { mutableStateOf<RemoteLoadState?>(null) }
    var pendingExportProfile by remember { mutableStateOf<IrRemoteProfile?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    val openIrDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importState = ImportState.Reading
            scope.launch {
                val parsed = runCatching {
                    val source = withContext(Dispatchers.IO) {
                        readTextBounded(context, uri)
                    }
                    val metadata = FlipperIrFileCodec.ImportMetadata(
                        profileId = UUID.randomUUID().toString(),
                        category = importCategory,
                        brand = importBrand.trim(),
                        model = importModel.trim().ifBlank { null },
                        displayName = importDisplayName.trim()
                    )
                    withContext(Dispatchers.Default) {
                        FlipperIrFileCodec.importFile(source, metadata)
                    }
                }.getOrElse { failure ->
                    importState = ImportState.Error(
                        failure.message ?: "Could not read the selected .ir file."
                    )
                    return@launch
                }

                importState = when (parsed) {
                    is FlipperIrFileCodec.ImportResult.Success ->
                        ImportState.Preview(parsed.profile, uri.lastPathSegment ?: "Selected .ir file")
                    is FlipperIrFileCodec.ImportResult.Failure ->
                        ImportState.Error(parsed.errors.joinToString("\n"))
                }
            }
        }
    }

    val createIrDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val profile = pendingExportProfile
        pendingExportProfile = null
        if (uri != null && profile != null) {
            scope.launch {
                exportMessage = "Preparing export…"
                val export = withContext(Dispatchers.Default) {
                    FlipperIrFileCodec.exportFile(profile)
                }
                when (export) {
                    is FlipperIrFileCodec.ExportResult.Failure -> {
                        exportMessage = export.errors.joinToString("\n")
                    }
                    is FlipperIrFileCodec.ExportResult.Success -> {
                        val writeResult = withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
                                    stream.write(export.content.toByteArray(Charsets.UTF_8))
                                } ?: error("Android could not open the selected destination.")
                            }
                        }
                        exportMessage = writeResult.fold(
                            onSuccess = {
                                buildString {
                                    append("Remote exported. Imported/exported codes remain unverified until tested against an appliance.")
                                    if (export.warnings.isNotEmpty()) {
                                        append("\n")
                                        append(export.warnings.joinToString("\n"))
                                    }
                                }
                            },
                            onFailure = { it.message ?: "Export failed." }
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(openProfileId) {
        currentRemoteState = null
        val profileId = openProfileId ?: return@LaunchedEffect
        currentRemoteState = RemoteLoadState.Loading
        val result = runCatching {
            withContext(Dispatchers.IO) { profileRepository.loadProfile(profileId) }
                ?: error("This saved remote profile is no longer available.")
        }
        currentRemoteState = result.fold(
            onSuccess = { RemoteLoadState.Loaded(it) },
            onFailure = { RemoteLoadState.Error(it.message ?: "Could not load this remote.") }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when {
                            openDaikinAc -> "Daikin AC · Experimental"
                            openProfileId != null ->
                                (currentRemoteState as? RemoteLoadState.Loaded)?.profile?.displayName ?: "Remote"
                            destination == Destination.TV_REMOTE -> "TV Remote"
                            else -> "OneRemote"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (openProfileId != null || openDaikinAc) {
                        OutlinedButton(onClick = {
                            openProfileId = null
                            openDaikinAc = false
                            selectedDaikinProfileId = null
                        }) {
                            Text("Back")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (openProfileId == null && !openDaikinAc) {
                NavigationBar {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = {
                                Icon(
                                    painter = painterResource(id = item.icon),
                                    contentDescription = null
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
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
            if (openDaikinAc) {
                DaikinAcRemoteScreen(
                    irHardware = irHardware,
                    dispatcher = daikinDispatcher,
                    profileDao = daikinProfileDao,
                    initialProfileId = selectedDaikinProfileId
                )
            } else if (openProfileId != null) {
                when (val remote = currentRemoteState) {
                    null, RemoteLoadState.Loading -> Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Loading saved remote…")
                    }
                    is RemoteLoadState.Error -> MessageScreen(remote.message)
                    is RemoteLoadState.Loaded -> RemoteScreen(
                        profile = remote.profile,
                        irHardware = irHardware,
                        dispatcher = dispatcher,
                        onExport = {
                            pendingExportProfile = remote.profile
                            val safeName = remote.profile.displayName
                                .replace(Regex("[^A-Za-z0-9_-]+"), "-")
                                .trim('-')
                                .ifBlank { "OneRemote" }
                            createIrDocument.launch("$safeName.ir")
                        },
                        exportMessage = exportMessage,
                        controlMappingDao = irProfileDao
                    )
                }
            } else {
                when (destination) {
                    Destination.DEVICES -> DevicesScreen(
                        devices = savedDevices,
                        daikinProfiles = savedDaikinProfiles,
                        onOpenRemote = { profileId -> openProfileId = profileId },
                        onOpenDaikinAc = {
                            selectedDaikinProfileId = null
                            openDaikinAc = true
                        },
                        onOpenDaikinProfile = { profileId ->
                            selectedDaikinProfileId = profileId
                            openDaikinAc = true
                        }
                    )
                    Destination.DISCOVER -> DiscoverScreen(
                        irHardware = irHardware,
                        onOpenDaikinAc = {
                            selectedDaikinProfileId = null
                            openDaikinAc = true
                        },
                        importDisplayName = importDisplayName,
                        onDisplayNameChange = { importDisplayName = it },
                        importBrand = importBrand,
                        onBrandChange = { importBrand = it },
                        importModel = importModel,
                        onModelChange = { importModel = it },
                        importCategory = importCategory,
                        onCategoryChange = { importCategory = it },
                        importState = importState,
                        isSaving = importBusy,
                        onChooseFile = {
                            importState = ImportState.Idle
                            openIrDocument.launch(arrayOf("*/*"))
                        },
                        onSaveProfile = { profile ->
                            if (!importBusy) {
                                importBusy = true
                                scope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            profileRepository.saveAsDevice(
                                                profile,
                                                System.currentTimeMillis()
                                            )
                                        }
                                    }
                                    importBusy = false
                                    importState = result.fold(
                                        onSuccess = {
                                            ImportState.Saved(profile.displayName)
                                        },
                                        onFailure = {
                                            ImportState.Error(it.message ?: "Could not save this profile.")
                                        }
                                    )
                                }
                            }
                        },
                        onClearImport = { importState = ImportState.Idle }
                    )
                    Destination.TV_REMOTE -> AndroidTvRemoteScreen()
                    Destination.SETTINGS -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun DevicesScreen(
    devices: List<SavedDeviceEntity>,
    daikinProfiles: List<DaikinAcProfileEntity>,
    onOpenRemote: (String) -> Unit,
    onOpenDaikinAc: () -> Unit,
    onOpenDaikinProfile: (String) -> Unit
) {
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

        if (devices.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Your saved remotes will appear here",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Configured remotes stay on this phone and work offline. Add an imported IR remote from Discover.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
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
                        if (device.profileId != null) {
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { onOpenRemote(device.profileId) }) {
                                Text("Open remote")
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Air conditioner profiles",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Saved settings stay offline. Compatibility remains unverified until your exact appliance and remote have been physically tested.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onOpenDaikinAc,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create or configure AC profile")
            }
        }

        if (daikinProfiles.isEmpty()) {
            item {
                Text(
                    "No saved AC profiles yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(daikinProfiles, key = { "daikin:${it.profileId}" }) { profile ->
                val setpoint = if (profile.mode == "DRY") {
                    "Dry · no setpoint"
                } else {
                    "${profile.temperatureCelsius} °C"
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            profile.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${profile.mode.lowercase().replaceFirstChar { it.uppercase() }} · $setpoint · ${profile.transportVariant.replace('_', ' ').lowercase()}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Compatibility unverified · Saved on this phone",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onOpenDaikinProfile(profile.profileId) }) {
                                Text("Open profile")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverScreen(
    irHardware: IrHardwareStatus?,
    onOpenDaikinAc: () -> Unit,
    importDisplayName: String,
    onDisplayNameChange: (String) -> Unit,
    importBrand: String,
    onBrandChange: (String) -> Unit,
    importModel: String,
    onModelChange: (String) -> Unit,
    importCategory: DeviceCategory,
    onCategoryChange: (DeviceCategory) -> Unit,
    importState: ImportState,
    isSaving: Boolean,
    onChooseFile: () -> Unit,
    onSaveProfile: (IrRemoteProfile) -> Unit,
    onClearImport: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Discover and add devices",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Find compatible Android TVs in the TV Remote tab, or import a Flipper Zero .ir profile for an IR-controlled device. Bluetooth discovery is not yet supported.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Built-in IR transmitter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
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
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Experimental Daikin AC controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "A dedicated full-state remote for two documented Daikin IR transport families. This is not a universal Daikin profile and has not been physically tested.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onOpenDaikinAc,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open experimental AC remote")
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Import an IR remote",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Select a Flipper Zero version-1 .ir remote file. Imported buttons are marked Unverified until their codes are tested with the actual appliance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = importDisplayName,
                        onValueChange = onDisplayNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Remote name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = importBrand,
                        onValueChange = onBrandChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Brand or source") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = importModel,
                        onValueChange = onModelChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Model (optional)") },
                        singleLine = true
                    )
                    Text("Device category", style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DeviceCategory.entries.forEach { category ->
                            FilterChip(
                                selected = importCategory == category,
                                onClick = { onCategoryChange(category) },
                                label = { Text(category.displayName) }
                            )
                        }
                    }
                    Button(
                        onClick = onChooseFile,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving && importDisplayName.isNotBlank() && importBrand.isNotBlank()
                    ) {
                        Text("Choose .ir file")
                    }
                    when (val state = importState) {
                        ImportState.Idle -> Unit
                        ImportState.Reading -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Reading and validating file…")
                        }
                        is ImportState.Error -> Text(
                            state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        is ImportState.Saved -> {
                            Text(
                                "${state.displayName} is saved on this phone and available offline.",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedButton(onClick = onClearImport) {
                                Text("Import another remote")
                            }
                        }
                        is ImportState.Preview -> {
                            Text(
                                "Preview: ${state.profile.displayName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${state.sourceName} · ${state.profile.buttons.size} buttons · all Unverified",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            state.profile.buttons.forEach { button ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(button.label, style = MaterialTheme.typography.bodyMedium)
                                    Text("Unverified", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                            Button(
                                onClick = { onSaveProfile(state.profile) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isSaving
                            ) {
                                if (isSaving) CircularProgressIndicator()
                                else Text("Save remote to Devices")
                            }
                            OutlinedButton(onClick = onClearImport) {
                                Text("Discard import")
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "The current importer accepts supported parsed protocols and raw timing waveforms. Unsupported parsed protocols are rejected rather than converted to guessed codes. IR learning still requires compatible receiver hardware.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RemoteScreen(
    profile: IrRemoteProfile,
    irHardware: IrHardwareStatus?,
    dispatcher: ProtocolCommandDispatcher,
    onExport: () -> Unit,
    exportMessage: String?,
    controlMappingDao: IrRemoteProfileDao
) {
    val scope = rememberCoroutineScope()
    val savedMappingEntities by controlMappingDao
        .observeControlMappings(profile.profileId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val availableButtons = remember(profile.buttons) {
        profile.buttons.associateBy { it.id }
    }
    val restoredMappings = remember(savedMappingEntities, profile.category, profile.buttons) {
        runCatching {
            RemoteControlMappingMapper.fromEntities(
                category = profile.category,
                availableButtonIds = availableButtons.keys,
                entities = savedMappingEntities
            )
        }
    }
    val mappings = restoredMappings.getOrNull().orEmpty()
    val mappingByRole = mappings.associateBy { it.role }

    var sendingButtonId by remember { mutableStateOf<String?>(null) }
    var sendFeedback by remember { mutableStateOf<String?>(null) }
    var editingLayout by rememberSaveable(profile.profileId) { mutableStateOf(false) }
    var draftMappings by remember(profile.profileId) {
        mutableStateOf<Map<RemoteControlRole, String>>(emptyMap())
    }
    var expandedRole by remember { mutableStateOf<RemoteControlRole?>(null) }
    var layoutSaving by remember { mutableStateOf(false) }
    var layoutFeedback by remember { mutableStateOf<String?>(null) }

    val supportedRoles = RemoteControlRole.forCategory(profile.category)
    val navigationRoles = listOf(
        RemoteControlRole.UP,
        RemoteControlRole.LEFT,
        RemoteControlRole.OK,
        RemoteControlRole.RIGHT,
        RemoteControlRole.DOWN
    )

    fun sendButton(button: IrRemoteButton) {
        if (sendingButtonId != null) return
        sendingButtonId = button.id
        sendFeedback = null
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    dispatcher.dispatch(button.command)
                }
            }
            sendFeedback = result.fold(
                onSuccess = { transmitResultMessage(it) },
                onFailure = { it.message ?: "Transmission failed." }
            )
            sendingButtonId = null
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(profile.displayName, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(profile.brand, profile.model).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Imported profile · command compatibility is Unverified",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            if (irHardware?.transmitterAvailable != true) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "No built-in IR transmitter is currently available. A compatible external IR transmitter is required to send these commands.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        item {
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text("Export remote (.ir)")
            }
            if (!exportMessage.isNullOrBlank()) {
                Text(
                    exportMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (profile.category == DeviceCategory.TELEVISION ||
            profile.category == DeviceCategory.SET_TOP_BOX
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (profile.category == DeviceCategory.TELEVISION) {
                                        "TV control layout"
                                    } else {
                                        "Set-top box control layout"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Only commands you assign below are enabled.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    draftMappings = mappings.associate {
                                        it.role to it.buttonId
                                    }
                                    editingLayout = !editingLayout
                                    expandedRole = null
                                    layoutFeedback = null
                                },
                                enabled = !layoutSaving
                            ) {
                                Text(if (editingLayout) "Close editor" else "Customize")
                            }
                        }

                        if (restoredMappings.isFailure) {
                            Text(
                                restoredMappings.exceptionOrNull()?.message
                                    ?: "Saved control layout is invalid. Reconfigure it before use.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (editingLayout) {
                            Text(
                                "Assign each role to the imported command that you know performs that action. OneRemote does not infer command meaning from the imported label.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            supportedRoles.forEach { role ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        role.label,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    BoxForDropdown(
                                        label = draftMappings[role]?.let { availableButtons[it]?.label }
                                            ?: "Choose command",
                                        expanded = expandedRole == role,
                                        onClick = { expandedRole = role },
                                        onDismiss = { expandedRole = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Not assigned") },
                                            onClick = {
                                                draftMappings = draftMappings - role
                                                expandedRole = null
                                            }
                                        )
                                        profile.buttons.forEach { button ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(button.label)
                                                        Text(
                                                            button.evidence.status.name
                                                                .replace('_', ' ')
                                                                .lowercase()
                                                                .replaceFirstChar { it.uppercase() },
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    draftMappings = draftMappings + (role to button.id)
                                                    expandedRole = null
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val requested = draftMappings.map { (role, buttonId) ->
                                            RemoteControlMapping(role, buttonId)
                                        }
                                        val entities = runCatching {
                                            RemoteControlMappingMapper.toEntities(
                                                profileId = profile.profileId,
                                                category = profile.category,
                                                availableButtonIds = availableButtons.keys,
                                                mappings = requested
                                            )
                                        }
                                        entities.onSuccess { records ->
                                            layoutSaving = true
                                            layoutFeedback = null
                                            scope.launch {
                                                val result = runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        controlMappingDao.replaceControlMappings(
                                                            profile.profileId,
                                                            records
                                                        )
                                                    }
                                                }
                                                layoutSaving = false
                                                if (result.isSuccess) {
                                                    editingLayout = false
                                                    layoutFeedback = "Remote layout saved on this phone."
                                                } else {
                                                    layoutFeedback = result.exceptionOrNull()?.message
                                                        ?: "Could not save the remote layout."
                                                }
                                            }
                                        }.onFailure {
                                            layoutFeedback = it.message
                                                ?: "The selected control mapping is invalid."
                                        }
                                    },
                                    enabled = !layoutSaving
                                ) {
                                    Text(if (layoutSaving) "Saving…" else "Save layout")
                                }
                                TextButton(
                                    onClick = {
                                        draftMappings = mappings.associate {
                                            it.role to it.buttonId
                                        }
                                        editingLayout = false
                                        expandedRole = null
                                        layoutFeedback = null
                                    },
                                    enabled = !layoutSaving
                                ) {
                                    Text("Cancel")
                                }
                            }
                        } else {
                            if (supportedRoles.isEmpty()) {
                                Text(
                                    "This device category uses its dedicated control screen.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else if (mappings.isEmpty()) {
                                Text(
                                    "No TV/STB controls have been assigned yet. Choose Customize and map imported commands to the controls you have verified on your original remote.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Text(
                                    "Navigation",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                RoleButtonRow(
                                    role = RemoteControlRole.UP,
                                    button = availableButtons[mappingByRole[RemoteControlRole.UP]?.buttonId],
                                    busy = sendingButtonId != null,
                                    onSend = { button -> sendButton(button) }
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RoleButton(
                                        role = RemoteControlRole.LEFT,
                                        button = availableButtons[mappingByRole[RemoteControlRole.LEFT]?.buttonId],
                                        busy = sendingButtonId != null,
                                        onSend = ::sendButton,
                                        modifier = Modifier.weight(1f)
                                    )
                                    RoleButton(
                                        role = RemoteControlRole.OK,
                                        button = availableButtons[mappingByRole[RemoteControlRole.OK]?.buttonId],
                                        busy = sendingButtonId != null,
                                        onSend = ::sendButton,
                                        modifier = Modifier.weight(1f)
                                    )
                                    RoleButton(
                                        role = RemoteControlRole.RIGHT,
                                        button = availableButtons[mappingByRole[RemoteControlRole.RIGHT]?.buttonId],
                                        busy = sendingButtonId != null,
                                        onSend = ::sendButton,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                RoleButtonRow(
                                    role = RemoteControlRole.DOWN,
                                    button = availableButtons[mappingByRole[RemoteControlRole.DOWN]?.buttonId],
                                    busy = sendingButtonId != null,
                                    onSend = { button -> sendButton(button) }
                                )

                                supportedRoles
                                    .filter { it !in navigationRoles }
                                    .groupBy { it.group }
                                    .forEach { (group, roles) ->
                                        Text(
                                            when (group) {
                                                RemoteControlRole.Group.SYSTEM -> "System and source"
                                                RemoteControlRole.Group.VOLUME -> "Volume"
                                                RemoteControlRole.Group.CHANNEL -> "Channels"
                                                RemoteControlRole.Group.NUMERIC -> "Number pad"
                                                RemoteControlRole.Group.PLAYBACK -> "Playback"
                                                RemoteControlRole.Group.NAVIGATION -> "Navigation"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        roles.chunked(2).forEach { rowRoles ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                rowRoles.forEach { role ->
                                                    RoleButton(
                                                        role = role,
                                                        button = availableButtons[mappingByRole[role]?.buttonId],
                                                        busy = sendingButtonId != null,
                                                        onSend = ::sendButton,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                if (rowRoles.size == 1) {
                                                    Spacer(Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                            }
                        }

                        if (!layoutFeedback.isNullOrBlank()) {
                            Text(
                                layoutFeedback.orEmpty(),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            "A role assignment changes the button layout only. It does not verify the imported IR command or the appliance model.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Text(
                "All imported commands",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Original imported commands remain available below, including commands not assigned to the custom layout.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(profile.buttons, key = { it.id }) { button ->
            RemoteButtonCard(
                button = button,
                isSending = sendingButtonId == button.id,
                canSend = sendingButtonId == null,
                onSend = { sendButton(button) }
            )
        }

        if (!sendFeedback.isNullOrBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Text(
                        sendFeedback.orEmpty(),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item {
            Text(
                "A successful Android transmit call only confirms that the signal was dispatched. It does not confirm that the appliance received it or changed state. No automatic retry is performed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BoxForDropdown(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column {
        OutlinedButton(onClick = onClick) {
            Text(label)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            content = content
        )
    }
}

@Composable
private fun RoleButtonRow(
    role: RemoteControlRole,
    button: IrRemoteButton?,
    busy: Boolean,
    onSend: (IrRemoteButton) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        RoleButton(
            role = role,
            button = button,
            busy = busy,
            onSend = onSend,
            modifier = Modifier.fillMaxWidth(0.42f)
        )
    }
}

@Composable
private fun RoleButton(
    role: RemoteControlRole,
    button: IrRemoteButton?,
    busy: Boolean,
    onSend: (IrRemoteButton) -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { button?.let(onSend) },
        enabled = button != null && !busy,
        modifier = modifier
    ) {
        Text(
            if (button == null) "Assign ${role.label}" else role.label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun RemoteButtonCard(
    button: IrRemoteButton,
    isSending: Boolean,
    canSend: Boolean,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(button.label, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium)
                Text(
                    button.evidence.status.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Button(onClick = onSend, enabled = canSend) {
                if (isSending) CircularProgressIndicator()
                else Text("Send")
            }
        }
    }
}

private fun transmitResultMessage(result: IrTransmitResult): String = when (result) {
    is IrTransmitResult.Sent -> buildString {
        append("IR signal sent. Appliance state is unconfirmed.")
        if (!result.dutyCycleApplied) {
            append(" The built-in Android transmitter cannot apply duty-cycle metadata from the imported file.")
        }
    }
    IrTransmitResult.NoTransmitter ->
        "No built-in IR transmitter is available. Connect a compatible external IR transmitter."
    is IrTransmitResult.UnsupportedFrequency ->
        "This phone does not report support for ${result.requestedHz} Hz. No signal was sent."
    is IrTransmitResult.Rejected ->
        "Command rejected before transmission: ${result.reason}"
    is IrTransmitResult.Failed ->
        "Transmission failed: ${result.reason}"
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

@Composable
private fun MessageScreen(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}

private fun readTextBounded(context: Context, uri: Uri): String {
    val maxBytes = FlipperIrFileCodec.MAX_FILE_CHARS * 4
    val output = ByteArrayOutputStream()
    context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(8_192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > maxBytes) {
                error("Selected file is too large to import.")
            }
            output.write(buffer, 0, count)
        }
    } ?: error("Android could not open the selected file.")

    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return decoder.decode(ByteBuffer.wrap(output.toByteArray())).toString()
}
