package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
// import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.PatrolEntry
import com.example.ui.ComplianceReport
import com.example.ui.PatrolViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape

// Theme Colors - Suit both light and dark device themes seamlessly
val ScreenBackground: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF0F172A) else Color(0xFFF8FAFC) // slate-900 vs slate-50
val CardSurface: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1E293B) else Color(0xFFFFFFFF)     // slate-800 vs pure white
val BorderColor: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF334155) else Color(0xFFE2E8F0)     // slate-700 vs slate-200
val TextPrimary: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF1F5F9) else Color(0xFF0F172A)     // slate-100 vs slate-900
val TextSecondary: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF94A3B8) else Color(0xFF52525B)   // slate-400 vs zinc-600
val CustomNeonCyan: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF38BDF8) else Color(0xFF0284C7)  // sky-400 vs sky-600
val CustomTeal: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF0D9488) else Color(0xFF0F766E)      // teal-600 vs teal-700
val CustomAmber: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFD97706) else Color(0xFFB45309)     // amber-600 vs amber-700
val CustomGreen: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF059669) else Color(0xFF047857)     // emerald-600 vs emerald-700
val CustomRed: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFDC2626) else Color(0xFFB91C1C)       // red-600 vs red-700
val CustomPurple: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF7C3AED) else Color(0xFF6D28D9)    // purple-600 vs purple-700
val BrightWhite: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF8FAFC) else Color(0xFF1E293B)     // slate-50 vs slate-800

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDark = isSystemInDarkTheme()
            val colors = if (isDark) {
                darkColorScheme(
                    background = ScreenBackground,
                    surface = CardSurface,
                    primary = CustomNeonCyan,
                    secondary = CustomTeal,
                    error = CustomRed
                )
            } else {
                lightColorScheme(
                    background = ScreenBackground,
                    surface = CardSurface,
                    primary = CustomNeonCyan,
                    secondary = CustomTeal,
                    error = CustomRed
                )
            }
            MaterialTheme(
                colorScheme = colors
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ScreenBackground
                ) {
                    AppNavigationContainer()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigationContainer() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = LocalContext.current as ComponentActivity
    val viewModel: PatrolViewModel = remember(owner) {
        androidx.lifecycle.ViewModelProvider(owner)[PatrolViewModel::class.java]
    }

    val patrolEntries by viewModel.patrolEntries.collectAsState()
    val isOnDuty by viewModel.isOnDuty.collectAsState()
    val timerTick by viewModel.timerTick.collectAsState()
    val nextDeadlineTime by viewModel.nextDeadlineTime.collectAsState()
    val secondsRemaining by viewModel.secondsRemaining.collectAsState()
    val missingPoints by viewModel.missingPoints.collectAsState()
    val customCheckpoints by viewModel.customCheckpoints.collectAsState()
    val complianceReport by viewModel.complianceReport.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val alarmSoundActive by viewModel.alarmSoundActive.collectAsState()

    var activeTab by remember { mutableStateOf("DASHBOARD") }

    // Dialog triggering states
    var activeLogPoint by remember { mutableStateOf<String?>(null) }
    var checkpointNotesInput by remember { mutableStateOf("") }
    var showEmergencyDialog by remember { mutableStateOf(false) }
    var emergencyNotesInput by remember { mutableStateOf("") }
    var showReportDialog by remember { mutableStateOf(false) }

    // Launchers for save/restore backups
    val createJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        val jsonLiteral = viewModel.getExportString()
                        outputStream.write(jsonLiteral.toByteArray())
                    }
                    Toast.makeText(context, "Patrol logs saved successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { inputStream ->
                        val jsonString = inputStream.bufferedReader().use { r -> r.readText() }
                        viewModel.restoreFromJson(
                            jsonString = jsonString,
                            onError = { error ->
                                Toast.makeText(context, "Restore failed: $error", Toast.LENGTH_LONG).show()
                            },
                            onSuccess = { inserted, skipped ->
                                Toast.makeText(
                                    context,
                                    "Database Restored! Loaded: $inserted checks. Skipped duplicates: $skipped",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Restore error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_patrologger),
                            contentDescription = "Patrologger logo",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PATROLOGGER",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = BrightWhite,
                            letterSpacing = 1.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardSurface),
                actions = {
                    if (canUndo) {
                        IconButton(onClick = {
                            viewModel.undoLastAction()
                            Toast.makeText(context, "Last action reversed!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Undo button",
                                tint = CustomNeonCyan
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Sleek compact tab navigation to safe height
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = CardSurface,
                border = BorderStroke(width = 1.dp, color = BorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dashboard tab
                    IconButton(
                        onClick = { activeTab = "DASHBOARD" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Dashboard",
                                tint = if (activeTab == "DASHBOARD") CustomNeonCyan else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Dashboard",
                                fontSize = 10.sp,
                                color = if (activeTab == "DASHBOARD") CustomNeonCyan else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Log History
                    IconButton(
                        onClick = { activeTab = "HISTORY" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "History Logs",
                                tint = if (activeTab == "HISTORY") CustomNeonCyan else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Patrol File",
                                fontSize = 10.sp,
                                color = if (activeTab == "HISTORY") CustomNeonCyan else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Settings Screen
                    IconButton(
                        onClick = { activeTab = "SETTINGS" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "System setup",
                                tint = if (activeTab == "SETTINGS") CustomNeonCyan else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Settings",
                                fontSize = 10.sp,
                                color = if (activeTab == "SETTINGS") CustomNeonCyan else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                "DASHBOARD" -> DashboardScreen(
                    viewModel = viewModel,
                    isOnDuty = isOnDuty,
                    timerTick = timerTick,
                    nextDeadlineTime = nextDeadlineTime,
                    secondsRemaining = secondsRemaining,
                    missingPoints = missingPoints,
                    customCheckpoints = customCheckpoints,
                    onLogPointClick = { activeLogPoint = it },
                    onToggleDuty = { viewModel.toggleDuty(context) },
                    onTriggerEmergency = { showEmergencyDialog = true }
                )
                "HISTORY" -> LogHistoryScreen(
                    patrolEntries = patrolEntries,
                    onDeleteLog = { viewModel.deleteLog(it) },
                    onExportJson = { createJsonLauncher.launch("patrol_logs_backup.json") },
                    onImportJson = { importJsonLauncher.launch(arrayOf("*/*")) },
                    onResetDb = {
                        viewModel.resetLogs()
                        Toast.makeText(context, "Database cleared! Press Undo to restore.", Toast.LENGTH_LONG).show()
                    },
                    onCalculateReport = {
                        viewModel.calculateMonthlyReport()
                        showReportDialog = true
                    }
                )
                "SETTINGS" -> SettingsScreen(
                    viewModel = viewModel,
                    customPoints = customCheckpoints,
                    onAddCheckpoint = { name -> viewModel.addCheckpoint(name, context) },
                    onRemoveCheckpoint = { name -> viewModel.removeCheckpoint(name, context) }
                )
            }

            // Quick log checkpoint dialog with custom notes
            if (activeLogPoint != null) {
                val pointName = activeLogPoint!!
                Dialog(onDismissRequest = { activeLogPoint = null }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "LOGGING SECURITY CHECK",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = CustomNeonCyan
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Point: $pointName",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrightWhite
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = checkpointNotesInput,
                                onValueChange = { checkpointNotesInput = it },
                                label = { Text("Activity notes (e.g., Secure)", fontSize = 11.sp) },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CustomNeonCyan,
                                    unfocusedBorderColor = BorderColor,
                                    focusedTextColor = BrightWhite,
                                    unfocusedTextColor = BrightWhite
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        activeLogPoint = null
                                        checkpointNotesInput = ""
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                    border = BorderStroke(1.dp, BorderColor)
                                ) {
                                    Text("CANCEL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        if (viewModel.canLogCheckpoint(pointName)) {
                                            viewModel.logCheckpoint(pointName, checkpointNotesInput)
                                            activeLogPoint = null
                                            checkpointNotesInput = ""
                                            Toast.makeText(context, "Checkpoint registered!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.playSoundAlarm()
                                            Toast.makeText(
                                                context,
                                                "⚠️ ACCESS DENIED: \"$pointName\" already tagged this hour!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            activeLogPoint = null
                                            checkpointNotesInput = ""
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = CustomNeonCyan)
                                ) {
                                    Text("SUBMIT", fontSize = 11.sp, fontWeight = FontWeight.Black, color = ScreenBackground)
                                }
                            }
                        }
                    }
                }
                       // Quick Emergency logging dialog
            if (showEmergencyDialog) {
                var selectedCategory by remember { mutableStateOf("Intruder") }
                val categoriesList = listOf("Intruder", "Fire", "Mob", "Suspicious Device", "Disaster", "Other")

                Dialog(onDismissRequest = { showEmergencyDialog = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1E1E)),
                        border = BorderStroke(1.5.dp, CustomRed),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🚨 CRITICAL SOS REPORTING",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Registers in database log and triggers urgent assistance via WhatsApp or Hotline.",
                                fontSize = 11.sp,
                                color = Color(0xFFFECACA),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "SELECT THREAT TYPE / CATEGORY:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFCA5A5),
                                modifier = Modifier.align(Alignment.Start).padding(bottom = 6.dp)
                            )

                            // 2-column Threat Type selector
                            categoriesList.chunked(2).forEach { rowList ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowList.forEach { categoryItem ->
                                        val isSelected = selectedCategory == categoryItem
                                        Card(
                                            onClick = { selectedCategory = categoryItem },
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) CustomRed else Color(0xFF4C2121)
                                            ),
                                            border = BorderStroke(1.dp, if (isSelected) Color.White else Color(0xFF7F1D1D))
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = categoryItem.uppercase(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (isSelected) Color.White else Color(0xFFFECACA)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = emergencyNotesInput,
                                onValueChange = { emergencyNotesInput = it },
                                label = { Text("Emergency notes details / situation", fontSize = 11.sp, color = Color(0xFFFCA5A5)) },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CustomRed,
                                    unfocusedBorderColor = Color(0xFF7F1D1D),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Three actions layout: Cancel (outline), Call Hotline (outline/red), Send WhatsApp (solid RED)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.logEmergency(selectedCategory, emergencyNotesInput)
                                        showEmergencyDialog = false
                                        
                                        // WhatsApp launch URL
                                        val rawPhone = viewModel.prefs.emergencyNumber
                                        val cleanPhone = rawPhone.replace(Regex("[^0-9]"), "")
                                        val msg = "Emergency assistance required. Category: ${selectedCategory.uppercase()}${if (emergencyNotesInput.isNotBlank()) " | Notes: $emergencyNotesInput" else ""}"
                                        val encodedMsg = Uri.encode(msg)
                                        val whatsappUrl = "https://wa.me/$cleanPhone?text=$encodedMsg"
                                        
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                data = Uri.parse(whatsappUrl)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open WhatsApp. Launching Dial pad...", Toast.LENGTH_SHORT).show()
                                            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                                data = Uri.parse("tel:${viewModel.prefs.emergencyNumber}")
                                            }
                                            context.startActivity(dialIntent)
                                        }
                                        emergencyNotesInput = ""
                                    },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CustomRed)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "WhatsApp",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("LOG & SEND WHATSAPP SOS", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.logEmergency(selectedCategory, emergencyNotesInput)
                                            showEmergencyDialog = false
                                            
                                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                                data = Uri.parse("tel:${viewModel.prefs.emergencyNumber}")
                                            }
                                            context.startActivity(intent)
                                            emergencyNotesInput = ""
                                        },
                                        modifier = Modifier.weight(1.2f).height(38.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFECACA)),
                                        border = BorderStroke(1.dp, Color(0xFF991B1B))
                                    ) {
                                        Text("CALL HOTLINE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            showEmergencyDialog = false
                                            emergencyNotesInput = ""
                                        },
                                        modifier = Modifier.weight(0.8f).height(38.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF9CA3AF)),
                                        border = BorderStroke(1.dp, Color(0xFF4B5563))
                                    ) {
                                        Text("CANCEL", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }      }

            // Compliance report display dialog
            if (showReportDialog && complianceReport != null) {
                val rep = complianceReport!!
                Dialog(onDismissRequest = { showReportDialog = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "MONTHLY COMPLIANCE REPORT",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = CustomNeonCyan
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Shift Sessions Starts:", fontSize = 12.sp, color = TextSecondary)
                                Text("${rep.totalStarts}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrightWhite)
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Checkpoints Tagged:", fontSize = 12.sp, color = TextSecondary)
                                Text("${rep.totalCheckpoints}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CustomGreen)
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Missed Guard Checks:", fontSize = 12.sp, color = TextSecondary)
                                Text("${rep.totalMissed}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CustomRed)
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Emergencies Actioned:", fontSize = 12.sp, color = TextSecondary)
                                Text("${rep.totalEmergencies}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CustomAmber)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = BorderColor, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "OVERALL COMPLIANCE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${rep.compliancePercent}%",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = if (rep.compliancePercent >= 85) CustomGreen else if (rep.compliancePercent >= 60) CustomAmber else CustomRed
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { showReportDialog = false },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CustomNeonCyan)
                            ) {
                                Text("DISMISS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ScreenBackground)
                            }
                        }
                    }
                }
            }

            // Alarm activation alert modal popup
            alarmSoundActive?.let { alarmName ->
                Dialog(onDismissRequest = { viewModel.stopAlarm() }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        border = BorderStroke(2.dp, CustomRed),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alarm triggered icon",
                                tint = CustomRed,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "ATTENTION REQUIRED",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "An active patrol alert has been detected:\n$alarmName",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { viewModel.stopAlarm() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CustomRed),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "SILENCE & DISMISS ALARM",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    viewModel: PatrolViewModel,
    isOnDuty: Boolean,
    timerTick: Long,
    nextDeadlineTime: Long,
    secondsRemaining: Long,
    missingPoints: List<String>,
    customCheckpoints: List<String>,
    onLogPointClick: (String) -> Unit,
    onToggleDuty: () -> Unit,
    onTriggerEmergency: () -> Unit
) {
    val dateString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timerTick))
    val calendarString = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(timerTick))
    val isPreferredActive = viewModel.isPreferredActive()

    val checkpointEntries = remember(viewModel.patrolEntries.collectAsState().value) {
        viewModel.patrolEntries.value.filter { it.status == "CHECKPOINT" }.take(4)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Identity Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_patrologger),
                        contentDescription = "Patrologger logo",
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .border(2.dp, CustomNeonCyan, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "PATROLOGGER",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = CustomNeonCyan,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Smart Guard Patrol & Verification",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // 'On Patrol' vs 'Off Duty' Status Indicator Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOnDuty) CustomGreen.copy(alpha = 0.12f) else BorderColor.copy(alpha = 0.15f)
                ),
                border = BorderStroke(1.5.dp, if (isOnDuty) CustomGreen else BorderColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isOnDuty) CustomGreen else Color.Gray, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOnDuty) "STATE: ON PATROL (ACTIVE SHIFT)" else "STATE: OFF DUTY (STANDBY SYSTEM)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isOnDuty) CustomGreen else TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Core Clock & Duty Banner Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = calendarString,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateString,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        color = CustomNeonCyan,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPreferredActive) "🟢 Preferred Window Active (:10 - :50 of every hour)"
                               else "🟡 Off-Peak Window Active (:51 - :09 of every hour)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPreferredActive) CustomGreen else CustomAmber
                    )
                }
            }
        }

        // Today's Patrol Summary Dashboard Component
        item {
            val completedToday = remember(viewModel.patrolEntries.collectAsState().value) {
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                viewModel.patrolEntries.value.filter { it.status == "CHECKPOINT" && it.timestamp >= todayStart }.size
            }

            val violationsToday = remember(viewModel.patrolEntries.collectAsState().value) {
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                viewModel.patrolEntries.value.filter { (it.status == "MISSED" || it.status == "EMERGENCY") && it.timestamp >= todayStart }.size
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_summary_card"),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(
                        text = "TODAY'S DASHBOARD OVERVIEW",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = CustomNeonCyan,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Stat 1: Completed Patrols Today
                        Card(
                            modifier = Modifier
                                .weight(1.0f)
                                .testTag("completed_today_stat_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (completedToday > 0) CustomGreen.copy(alpha = 0.08f) else Color.Transparent
                            ),
                            border = BorderStroke(1.dp, if (completedToday > 0) CustomGreen.copy(alpha = 0.4f) else BorderColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Completed Today",
                                        tint = if (completedToday > 0) CustomGreen else TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "COMPLETED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$completedToday",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (completedToday > 0) CustomGreen else TextPrimary
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = if (completedToday == 1) "1 Checkpoint" else "$completedToday Points",
                                    fontSize = 8.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Stat 2: Violations Today
                        Card(
                            modifier = Modifier
                                .weight(1.0f)
                                .testTag("violations_today_stat_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (violationsToday > 0) CustomRed.copy(alpha = 0.08f) else Color.Transparent
                            ),
                            border = BorderStroke(1.dp, if (violationsToday > 0) CustomRed.copy(alpha = 0.4f) else BorderColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Violations Today",
                                        tint = if (violationsToday > 0) CustomRed else TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "VIOLATIONS",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$violationsToday",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (violationsToday > 0) CustomRed else TextPrimary
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = if (violationsToday == 1) "1 Overdue point" else "$violationsToday Overdue/SOS",
                                    fontSize = 8.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Stat 3: Next Scheduled Checkpoint / Deadline countdown
                        Card(
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("next_scheduled_stat_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOnDuty && secondsRemaining < 60) CustomRed.copy(alpha = 0.08f)
                                                 else if (isOnDuty) CustomNeonCyan.copy(alpha = 0.05f)
                                                 else Color.Transparent
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isOnDuty && secondsRemaining < 60) CustomRed.copy(alpha = 0.5f)
                                        else if (isOnDuty) CustomNeonCyan.copy(alpha = 0.3f)
                                        else BorderColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Next deadline",
                                        tint = if (isOnDuty) CustomNeonCyan else TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "NEXT ROUND",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                if (isOnDuty) {
                                    val m = secondsRemaining / 60
                                    val s = secondsRemaining % 60
                                    val countdownStr = String.format(Locale.getDefault(), "%02d:%02d", m, s)
                                    
                                    val formattedNextHour = if (nextDeadlineTime > 0) {
                                        SimpleDateFormat("HH:00", Locale.getDefault()).format(Date(nextDeadlineTime))
                                    } else {
                                        "..."
                                    }

                                    Text(
                                        text = countdownStr,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (secondsRemaining < 60) CustomRed else CustomAmber
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = "Due at $formattedNextHour",
                                        fontSize = 8.sp,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Text(
                                        text = "STANDBY",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = "Inactive",
                                        fontSize = 8.sp,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action controls (On/Off Duty & Emergency Panic)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Toggle Duty button
                Button(
                    onClick = onToggleDuty,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("toggle_duty_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOnDuty) CustomRed else CustomTeal
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (isOnDuty) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = "Duty active indicator"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isOnDuty) "OFF DUTY" else "START SHIFT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = BrightWhite
                    )
                }

                // Emergency Panic Hotline Button
                Button(
                    onClick = onTriggerEmergency,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("panic_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CustomAmber),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Emergency panic hotline icon"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PANIC / SOS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = BrightWhite
                    )
                }
            }
        }

        // Real-time Countdown Stopwatch Panel (Only shown onDuty)
        if (isOnDuty) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, if (secondsRemaining < 60) CustomRed else BorderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Timer countdown icon",
                                tint = if (secondsRemaining < 60) CustomRed else CustomNeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "NEXT COMPLIANCE CHECK DEADLINE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = if (secondsRemaining < 60) CustomRed else CustomNeonCyan
                            )
                        }

                        val formattedDeadline = if (nextDeadlineTime > 0) {
                            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(nextDeadlineTime))
                        } else {
                            "Syncing..."
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = formattedDeadline,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrightWhite
                            )

                            val m = secondsRemaining / 60
                            val s = secondsRemaining % 60
                            val countdownStr = String.format(Locale.getDefault(), "%02d:%02d", m, s)

                            Text(
                                text = "T-Minus: $countdownStr",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (secondsRemaining < 60) CustomRed else CustomAmber
                            )
                        }

                        // Display overdue points list
                        if (missingPoints.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "⚠️ MISSED POINTS FOR THIS HOUR ONLY:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = CustomRed
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                missingPoints.forEach { point ->
                                    Text(
                                        text = "• $point (Missed Check-in)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFFCA5A5)
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "✨ All points are successfully tagged for this hour!",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CustomGreen
                            )
                        }
                    }
                }
            }

            // Quick log buttons for active checkpoints
            item {
                Text(
                    text = "TAP POINT TO LOG CHECK-IN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            if (customCheckpoints.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardSurface, RoundedCornerShape(10.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active checkpoints configured.\nGo to Settings to add points.",
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customCheckpoints.forEach { point ->
                            val canLog = viewModel.canLogCheckpoint(point)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = true) {
                                        onLogPointClick(point)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (canLog) CardSurface else Color(0xFF1E293B).copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (canLog) BorderColor else Color(0xFF334155).copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (canLog) Icons.Default.LocationOn else Icons.Default.Lock,
                                            contentDescription = "Location tracker icon",
                                            tint = if (canLog) CustomNeonCyan else TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = point,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (canLog) BrightWhite else TextSecondary
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (canLog) CustomNeonCyan.copy(alpha = 0.12f) else Color.DarkGray.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = if (canLog) "READY" else "TAGGED (HR)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (canLog) CustomNeonCyan else TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Off duty informational card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurface, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Shift status off-duty banner icon",
                            tint = CustomTeal,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "GUARD SYSTEM CURRENTLY STANDBY",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrightWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Press START SHIFT above to begin real-time patrol stopwatch monitoring.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // List view displaying the latest 4 recorded checkpoints
        item {
            Text(
                text = "LATEST 4 RECORDED CHECKPOINTS",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = CustomNeonCyan,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        }

        if (checkpointEntries.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No checkpoints recorded yet for this shift.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    checkpointEntries.forEach { entry ->
                        val timeStr = SimpleDateFormat("HH:mm:ss (EEE)", Locale.getDefault()).format(Date(entry.timestamp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardSurface),
                            border = BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = CustomNeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = entry.checkpoint,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrightWhite
                                    )
                                }
                                Text(
                                    text = timeStr,
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Observation Notes Input Card (not in patrol logs but in exact hour group)
        item {
            var observationInput by remember { mutableStateOf("") }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "ADD SHIFT OBSERVATION",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = CustomNeonCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Record notes of active observations. They won't affect patrol compliance statistics but will be logged in the exact hour group.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = observationInput,
                        onValueChange = { observationInput = it },
                        placeholder = { Text("Describe security observation...", fontSize = 13.sp) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CustomNeonCyan,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = BrightWhite,
                            unfocusedTextColor = BrightWhite
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (observationInput.isNotBlank()) {
                                viewModel.logObservation(observationInput)
                                observationInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CustomNeonCyan),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("LOG OBSERVATION", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ScreenBackground)
                    }
                }
            }
        }

        // Standard Support Footer at the end of the Dashboard screen
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = BorderColor, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "EAGLEEYE DISPATCH SUPPORT",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "eagleeyetechsolu@gmail.com",
                    color = CustomNeonCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Direct Hotline: ${viewModel.prefs.emergencyNumber}",
                    color = CustomNeonCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "© 2026 EagleEye Technical Solutions | Ver 2.5",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun LogHistoryScreen(
    patrolEntries: List<PatrolEntry>,
    onDeleteLog: (Int) -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onResetDb: () -> Unit,
    onCalculateReport: () -> Unit
) {
    val groupedLogs = remember(patrolEntries) {
        patrolEntries.groupBy { entry ->
            val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(entry.timestamp))
            val dayLabel = SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault()).format(Date(entry.timestamp))
            val hourKey = SimpleDateFormat("HH", Locale.getDefault()).format(Date(entry.timestamp))
            val hourLabel = SimpleDateFormat("HH:00", Locale.getDefault()).format(Date(entry.timestamp))
            Pair(Pair(dayKey, hourKey), Pair(dayLabel, hourLabel))
        }.mapValues { (_, entries) ->
            entries.sortedByDescending { it.timestamp }
        }.toList().sortedWith { a, b ->
            val compDay = b.first.first.first.compareTo(a.first.first.first)
            if (compDay != 0) compDay else b.first.first.second.compareTo(a.first.first.second)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // Log Actions Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = onCalculateReport,
                        colors = ButtonDefaults.buttonColors(containerColor = CustomTeal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("report_button"),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "Assessment icon", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("REPORT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onExportJson,
                        colors = ButtonDefaults.buttonColors(containerColor = CustomNeonCyan),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_button"),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Save icon", modifier = Modifier.size(14.dp), tint = ScreenBackground)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("EXPORT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ScreenBackground)
                    }

                    Button(
                        onClick = onImportJson,
                        colors = ButtonDefaults.buttonColors(containerColor = CustomPurple),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_button"),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Backup restore", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("RESTORE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedButton(
                        onClick = onResetDb,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CustomRed),
                        border = BorderStroke(1.dp, CustomRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(0.6f)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Reset", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RESET HISTORY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Log Items List
        Text(
            text = "SHIFT RECORDS LOGS (GROUPED BY DATE & HOUR)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        if (groupedLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(CardSurface, RoundedCornerShape(12.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No patrol logs recorded.\nChange shift mode or log checkpoints.",
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedLogs.forEach { group ->
                    val (dayLabel, hourLabel) = group.first.second
                    val entries = group.second
                    
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = CustomNeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = dayLabel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrightWhite
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(CustomNeonCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = hourLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = CustomNeonCyan
                                    )
                                }
                            }
                        }
                    }
                    
                    items(entries) { item ->
                        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        val timeStr = sdf.format(Date(item.timestamp))

                        val (statusColor, statusIcon) = when (item.status) {
                            "START" -> Pair(CustomGreen, Icons.Default.PlayArrow)
                            "FINISH" -> Pair(CustomAmber, Icons.Default.Close)
                            "EMERGENCY" -> Pair(CustomRed, Icons.Default.Warning)
                            "MISSED" -> Pair(CustomRed, Icons.Default.Close)
                            "OBSERVATION" -> Pair(CustomPurple, Icons.Default.Edit)
                            else -> Pair(CustomNeonCyan, Icons.Default.Done)
                        }

                        val isViolation = item.status == "MISSED" || item.status == "EMERGENCY"

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isViolation) Color(0xFF2E1919) else if (item.status == "OBSERVATION") Color(0xFF1E192E) else CardSurface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isViolation) CustomRed else if (item.status == "OBSERVATION") CustomPurple else BorderColor
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(50)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = "Status logo descriptor",
                                        tint = statusColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.checkpoint,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.status == "OBSERVATION") Color.White else BrightWhite
                                        )

                                        Text(
                                            text = if (item.status == "MISSED") "VIOLATION" else item.status,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = statusColor,
                                            modifier = Modifier
                                                .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }

                                    if (item.notes.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.notes,
                                            fontSize = 13.sp,
                                            color = if (item.status == "OBSERVATION") Color.White else TextPrimary
                                        )
                                    }

                                    if (isViolation) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val valRange = try {
                                            // Since slots are on precise hours, slotEnd is the timestamp.
                                            // The hour slot preceding slotEnd is when the violation took place.
                                            val hourStr = SimpleDateFormat("HH", Locale.getDefault()).format(Date(item.timestamp))
                                            val hourInt = hourStr.toInt()
                                            val prevHour = (hourInt - 1 + 24) % 24
                                            String.format(Locale.getDefault(), "%02d:00 - %02d:00", prevHour, hourInt)
                                        } catch (e: Exception) {
                                            SimpleDateFormat("HH:00", Locale.getDefault()).format(Date(item.timestamp))
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .background(CustomRed.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = CustomRed,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Hour Violated: $valRange",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CustomRed
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = timeStr,
                                        fontSize = 11.sp,
                                        color = if (item.status == "OBSERVATION") Color(0xFFC084FC) else TextSecondary
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                IconButton(
                                    onClick = { onDeleteLog(item.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete entry selector icon",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: PatrolViewModel,
    customPoints: List<String>,
    onAddCheckpoint: (String) -> Unit,
    onRemoveCheckpoint: (String) -> Unit
) {
    var checkPointNameInput by remember { mutableStateOf("") }

    var intervalInput by remember { mutableStateOf(viewModel.prefs.patrolInterval.toString()) }
    var startHourInput by remember { mutableStateOf(viewModel.prefs.startHour.toString()) }
    var endHourInput by remember { mutableStateOf(viewModel.prefs.endHour.toString()) }
    var emergencyContactInput by remember { mutableStateOf(viewModel.prefs.emergencyNumber) }
    var alarmVolumeInput by remember { mutableStateOf(viewModel.prefs.alarmVolume / 100f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section A: Customizable Checkpoints Points Manager
        item {
            Text(
                text = "MANAGE PATROL CHECKPOINTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = CustomNeonCyan,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Customize active locations. Guards must check-in each of these within the custom interval to avoid missed penalties.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Edit Checkpoint Add bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = checkPointNameInput,
                            onValueChange = { checkPointNameInput = it },
                            label = { Text("New point (e.g., Gate C)", fontSize = 11.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CustomNeonCyan,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = BrightWhite,
                                unfocusedTextColor = BrightWhite
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (checkPointNameInput.isNotBlank()) {
                                    onAddCheckpoint(checkPointNameInput)
                                    checkPointNameInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CustomNeonCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("ADD", fontSize = 11.sp, color = ScreenBackground, fontWeight = FontWeight.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = BorderColor, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (customPoints.isEmpty()) {
                        Text(
                            text = "No checkpoints configured currently.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            customPoints.forEach { point ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ScreenBackground, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = point,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = BrightWhite
                                    )
                                    IconButton(
                                        onClick = { onRemoveCheckpoint(point) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove location point",
                                            tint = CustomRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section B: Patrol Frequency Tuning Settings
        item {
            Text(
                text = "PATROL INTERVAL SETTINGS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = CustomNeonCyan,
                modifier = Modifier.padding(bottom = 2.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Stopwatch interval frequency for mandatory checkpoint scans:",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = intervalInput,
                        onValueChange = { intervalInput = it },
                        label = { Text("Frequency (in minutes)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CustomNeonCyan,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = BrightWhite,
                            unfocusedTextColor = BrightWhite
                        )
                    )
                }
            }
        }

        // Section C: Operating Shift Hour Range Slots
        item {
            Text(
                text = "SHIFT TIME RANGE SLOTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = CustomNeonCyan,
                modifier = Modifier.padding(bottom = 2.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Night cover hours range configurations (from 0 to 23 hours):",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = startHourInput,
                            onValueChange = { startHourInput = it },
                            label = { Text("Start (0-23)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CustomNeonCyan,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = BrightWhite,
                                unfocusedTextColor = BrightWhite
                            )
                        )

                        OutlinedTextField(
                            value = endHourInput,
                            onValueChange = { endHourInput = it },
                            label = { Text("End (0-23)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CustomNeonCyan,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = BrightWhite,
                                unfocusedTextColor = BrightWhite
                            )
                        )
                    }
                }
            }
        }

        // Section D: Direct Dispatch SOS Emergency Phone Config
        item {
            Text(
                text = "EMERGENCY HOTLINE SETUP",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = CustomNeonCyan,
                modifier = Modifier.padding(bottom = 2.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Specify target phone number that is dialled upon initiating Panic / SOS alarms:",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = emergencyContactInput,
                        onValueChange = { emergencyContactInput = it },
                        label = { Text("Direct hotline phone", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CustomNeonCyan,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = BrightWhite,
                            unfocusedTextColor = BrightWhite
                        )
                    )
                }
            }
        }

        // Section E: Compliance Alarm Sound Level
        item {
            Text(
                text = "ALARM SYSTEM VOLUME LEVEL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = CustomNeonCyan,
                modifier = Modifier.padding(bottom = 2.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Fine-tune the decibel level of missed check-in notifications (set to ${(alarmVolumeInput * 100).toInt()}%):",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔈 Min",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = alarmVolumeInput,
                            onValueChange = { alarmVolumeInput = it },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = CustomNeonCyan,
                                activeTrackColor = CustomNeonCyan,
                                inactiveTrackColor = BorderColor
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🔊 Max",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Action: Save configuration parameters
        item {
            Button(
                onClick = {
                    val intVal = intervalInput.toIntOrNull() ?: 15
                    val sHr = startHourInput.toIntOrNull() ?: 20
                    val eHr = endHourInput.toIntOrNull() ?: 6

                    if (sHr !in 0..23 || eHr !in 0..23) {
                        Toast.makeText(viewModel.getApplication(), "Error: Time values must range between 0 and 23!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    viewModel.prefs.patrolInterval = intVal
                    viewModel.prefs.startHour = sHr
                    viewModel.prefs.endHour = eHr
                    viewModel.prefs.emergencyNumber = emergencyContactInput
                    viewModel.prefs.alarmVolume = (alarmVolumeInput * 100f).toInt()

                    viewModel.loadEntries() // recalculate stats based on new settings
                    Toast.makeText(viewModel.getApplication(), "System settings saved securely!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("save_settings_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CustomNeonCyan)
            ) {
                Text(
                    text = "SAVE CONFIGURATION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = ScreenBackground
                )
            }
        }

        // Logo Footer at bottom of Settings screen
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Divider(color = BorderColor, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_patrologger),
                    contentDescription = "Patrologger logo",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "EAGLEEYE TECHNICAL SOLUTIONS",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "eagleeyetechsolu@gmail.com",
                    color = CustomNeonCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Direct Hotline: ${viewModel.prefs.emergencyNumber}",
                    color = CustomNeonCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "© 2026 EagleEye Technical Solutions | Ver 2.5",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// FlowRow layout helper for simple, non-deprecated responsive inline layouts
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    activityTab: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}
