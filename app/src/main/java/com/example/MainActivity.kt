package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import java.util.Calendar
import java.util.Date
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

// High Density Design Styling Constants
val ThBg = Color(0xFFFEFBFF)
val ThTextMain = Color(0xFF1C1B1F)
val ThTextSecondary = Color(0xFF49454F)
val ThPurpleLight = Color(0xFFEADDFF)
val ThPurpleDeep = Color(0xFF21005D)
val ThPurpleBrand = Color(0xFF6750A4)
val ThBorderNeutral = Color(0xFFCAC4D0)
val ThDangerBg = Color(0xFFFEEBEB)
val ThDangerText = Color(0xFFB3261E)
val ThSuccessText = Color(0xFF386A20)
val ThSuccessBg = Color(0xFFD1FAE5)
val ThGridBg = Color(0xFFE7E0EC)

class MainActivity : ComponentActivity() {
    private val viewModel: PatrolViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = ThBg
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(ThBg)
                    ) {
                        PatrolTrackerScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatrolTrackerScreen(viewModel: PatrolViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ViewModel States
    val offDuty by viewModel.offDuty.collectAsStateWithLifecycle()
    val emergencyNum by viewModel.emergencyNumber.collectAsStateWithLifecycle()
    val activePoints by viewModel.points.collectAsStateWithLifecycle()
    val dutyStart by viewModel.dutyStartTime.collectAsStateWithLifecycle()
    val allEntries by viewModel.allEntries.collectAsStateWithLifecycle()
    val stats by viewModel.statsState.collectAsStateWithLifecycle()
    val customPointNames by viewModel.customPointNames.collectAsStateWithLifecycle()
    val canUndoState by viewModel.canUndo.collectAsStateWithLifecycle()
    val canUndoReset by viewModel.canUndoReset.collectAsStateWithLifecycle()

    // Dialog & Alarm States
    val alarmActive by viewModel.alarmActive.collectAsStateWithLifecycle()
    val alarmMsg by viewModel.alarmMessage.collectAsStateWithLifecycle()
    val monthlyReport by viewModel.monthlyReportData.collectAsStateWithLifecycle()

    // Timer texts
    val countdownText by viewModel.countdownText.collectAsStateWithLifecycle()
    val deadlineText by viewModel.deadlineText.collectAsStateWithLifecycle()
    val hourIndicatorText by viewModel.hourIndicatorText.collectAsStateWithLifecycle()
    val windowStatusText by viewModel.windowStatusText.collectAsStateWithLifecycle()
    val isWindowActive by viewModel.isWindowActive.collectAsStateWithLifecycle()

    // Tab State: 0 = DASH, 1 = BACKUP, 2 = SETTINGS
    var selectedTab by remember { mutableStateOf(0) }

    // UI Inputs & UI States
    var customNoteInput by remember { mutableStateOf("") }
    var emergencyInput by remember { mutableStateOf(emergencyNum) }
    var activeConfirmingPoint by remember { mutableStateOf<Int?>(null) }

    // Backup Import Result Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val rStream = context.contentResolver.openInputStream(it)
                rStream?.use { stream ->
                    val textContent = stream.bufferedReader().use { br -> br.readText() }
                    viewModel.importBackup(
                        jsonString = textContent,
                        onSuccess = { count ->
                            Toast.makeText(context, "Imported $count logs successfully!", Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            Toast.makeText(context, "Import failed: $err", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    val createJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            pendingExportJson?.let { jsonContent ->
                try {
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        stream.write(jsonContent.toByteArray())
                    }
                    Toast.makeText(context, "Backup saved to your storage!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to save file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Initialize emergency number once loaded
    LaunchedEffect(emergencyNum) {
        emergencyInput = emergencyNum
    }

    val scrollState = rememberScrollState()

    LaunchedEffect(selectedTab) {
        scrollState.scrollTo(0)
    }

    // Main App Container mapping High Density Mockup Flex Structure
    Column(modifier = Modifier.fillMaxSize()) {
        
        // 1. Static Layout Header Section (Consistent across all Tabs)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .background(ThBg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Patrol Tracker Logo",
                    modifier = Modifier.size(38.dp)
                )
                Column {
                    Text(
                        text = "Patrol Tracker",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = ThTextMain,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "VERSION 5.5 • OFFLINE MODE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThPurpleBrand,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // High Contrast Header Panic Button
            Button(
                onClick = {
                    val targetNum = emergencyNum.replace("[^0-9]".toRegex(), "")
                    if (targetNum.isBlank()) {
                        Toast.makeText(context, "No emergency number saved! Add under Settings.", Toast.LENGTH_LONG).show()
                    } else {
                        try {
                            val messageStr = Uri.encode("🚨 Emergency Alert: Immediate assistance required!")
                            val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$targetNum?text=$messageStr"))
                            context.startActivity(waIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open WhatsApp. Ensure it is installed.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ThDangerText),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("panic_button")
            ) {
                Text("PANIC", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Divider under the main header
        Divider(color = ThBorderNeutral.copy(alpha = 0.5f), thickness = 1.dp)

        // 2. Tab-dependent Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (selectedTab) {
                    0 -> { // ================= DASH TAB =================
                        // A. Duty Status Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (offDuty) Color(0xFFF3EDF7) else ThPurpleLight
                            ),
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Duty Action row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(
                                                    color = if (offDuty) ThDangerText else ThSuccessText,
                                                    shape = androidx.compose.foundation.shape.CircleShape
                                                )
                                        )
                                        Text(
                                            text = "Status: ${if (offDuty) "OFF DUTY" else "ON DUTY"}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = if (offDuty) ThTextMain else ThPurpleDeep
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            if (offDuty) viewModel.setOnDuty() else viewModel.setOffDuty()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (offDuty) ThPurpleDeep else Color(0xFFB3261E).copy(alpha = 0.1f)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .testTag("toggle_shift_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = if (offDuty) "START SHIFT" else "END SHIFT",
                                            color = if (offDuty) Color.White else ThDangerText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Next Deadline & Countdowns
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column {
                                        Text(
                                            text = "NEXT DEADLINE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (offDuty) ThTextSecondary else ThPurpleDeep.copy(alpha = 0.8f),
                                            letterSpacing = 0.5.sp
                                        )
                                        
                                        // Robust extract of HH:MM:SS from deadlineText
                                        val timeRegex = Regex("\\d{2}:\\d{2}:\\d{2}")
                                        val match = timeRegex.find(deadlineText)
                                        val formattedDeadline = match?.value ?: "18:00:00"

                                        Text(
                                            text = if (offDuty) "--:--:--" else formattedDeadline,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (offDuty) ThTextSecondary else ThPurpleDeep
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "REMAINING",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (offDuty) ThTextSecondary else ThPurpleDeep.copy(alpha = 0.8f),
                                            letterSpacing = 0.5.sp
                                        )
                                        val cleanRemaining = countdownText.replace("⏰", "").replace("remaining", "").trim()
                                        Text(
                                            text = if (offDuty) "Inactive" else if (countdownText.contains("passed")) "Overdue" else cleanRemaining,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (offDuty) ThTextSecondary else ThSuccessText
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Active indicator bar tracking
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .background(if (offDuty) ThBorderNeutral.copy(alpha = 0.3f) else Color(0xFFD0BCFF), RoundedCornerShape(100.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(if (offDuty) 0f else 0.65f)
                                            .background(if (offDuty) ThTextSecondary else ThPurpleBrand, RoundedCornerShape(100.dp))
                                    )
                                }
                            }
                        }

                        // B. Points Control Grid (Compact 5-column Layout)
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        if (activePoints.isEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, ThBorderNeutral),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "No active patrol points configured.",
                                    fontSize = 13.sp,
                                    color = ThTextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                )
                            }
                        } else {
                            val rowChunkSize = 5
                            val rows = activePoints.chunked(rowChunkSize)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                rows.forEach { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        row.forEach { pNum ->
                                            val formattedNum = String.format("%02d", pNum)
                                            val pointName = customPointNames[pNum] ?: formattedNum
                                            Button(
                                                onClick = { activeConfirmingPoint = pNum },
                                                enabled = !offDuty,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = ThGridBg,
                                                    disabledContainerColor = ThBorderNeutral.copy(alpha = 0.3f)
                                                ),
                                                border = BorderStroke(1.dp, ThBorderNeutral),
                                                shape = RoundedCornerShape(16.dp),
                                                contentPadding = PaddingValues(2.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("point_grid_$pNum")
                                            ) {
                                                Text(
                                                    text = pointName,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = if (pointName.length > 5) 10.sp else 12.sp,
                                                    maxLines = 1,
                                                    color = if (offDuty) ThTextSecondary else ThPurpleBrand
                                                )
                                            }
                                        }
                                        // Empty buffers
                                        if (row.size < rowChunkSize) {
                                            repeat(rowChunkSize - row.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // C. Statistics & Info Bento Grid
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Left Bento: Recent Logs summary list
                            Card(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(190.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, ThBorderNeutral)
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Header title band
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF7F2FA))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "RECENT LOG",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = ThTextSecondary,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = "VIEW",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ThPurpleBrand
                                        )
                                    }

                                    // Quick scroll of active indices
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (allEntries.isEmpty()) {
                                            Text(
                                                text = "Pending patrol logs...",
                                                fontSize = 11.sp,
                                                color = ThTextSecondary.copy(alpha = 0.6f),
                                                modifier = Modifier.padding(top = 16.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        } else {
                                            allEntries.take(3).forEach { entry ->
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        val labelVal = entry.point?.let { customPointNames[it] ?: it.toString() } ?: ""
                                                        Text(
                                                            text = if (entry.note != null) "Note" else "Point $labelVal",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = ThTextMain
                                                        )
                                                        Text(
                                                            text = entry.time,
                                                            fontSize = 9.sp,
                                                            color = ThTextSecondary
                                                        )
                                                    }
                                                    Text(
                                                        text = if (entry.outOfWindow) "Outside Window ⚠️" else "Within Window",
                                                        fontSize = 9.sp,
                                                        color = if (entry.outOfWindow) ThDangerText else ThSuccessText,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                Divider(color = ThBorderNeutral.copy(alpha = 0.3f), thickness = 0.5.dp)
                                            }
                                        }
                                    }
                                }
                            }

                            // Right Bento: Metrics stacking (Violations + Active hours)
                            Column(
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(190.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Violations counts
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = ThDangerBg),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "VIOLATIONS",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = ThDangerText,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = String.format("%02d", stats.totalViolations),
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = ThDangerText
                                        )
                                        Text(
                                            text = "Gaps > 65m",
                                            fontSize = 8.sp,
                                            color = ThDangerText.copy(alpha = 0.7f),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Worked Hours index
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "ACTIVE HOURS",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF4B5563),
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1f", stats.hoursWorkedToday.toDouble()),
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF1F2937)
                                        )
                                        Text(
                                            text = "On-Duty Shift",
                                            fontSize = 8.sp,
                                            color = Color(0xFF6B7280),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // D. Quick Note Controls Row
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customNoteInput,
                                onValueChange = { customNoteInput = it },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(52.dp),
                                placeholder = { Text("Quick note...", fontSize = 12.sp) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                                shape = RoundedCornerShape(16.dp),
                                enabled = !offDuty,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = ThTextMain,
                                    unfocusedTextColor = ThTextMain,
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = ThPurpleBrand,
                                    unfocusedBorderColor = ThBorderNeutral
                                ),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (customNoteInput.isNotBlank()) {
                                        viewModel.logCustomNote(customNoteInput)
                                        customNoteInput = ""
                                        Toast.makeText(context, "Logged note!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !offDuty && customNoteInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ThPurpleBrand,
                                    disabledContainerColor = ThBorderNeutral.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("submit_note_button")
                            ) {
                                Text("SAVE ENTRY", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                        }

                        // E. Complete Patrol Logs Grouped List Block
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, ThBorderNeutral)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Guard Records History",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ThTextMain
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                if (allEntries.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No patrol entries registered yet.",
                                            color = ThTextSecondary.copy(alpha = 0.7f),
                                            fontSize = 12.sp
                                        )
                                    }
                                } else {
                                    val groupedLogs = remember(allEntries) {
                                        val map = mutableMapOf<String, MutableMap<Int, MutableList<PatrolEntry>>>()
                                        allEntries.forEach { e -> _addEntryToMap(map, e) }
                                        map
                                    }

                                    groupedLogs.keys.sortedDescending().forEach { dateLabel ->
                                        val dateHourMap = groupedLogs[dateLabel] ?: return@forEach

                                        Text(
                                            text = "Date: $dateLabel",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = ThPurpleBrand,
                                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                        )
                                        Divider(color = ThBorderNeutral.copy(alpha = 0.4f), thickness = 0.5.dp)

                                        dateHourMap.keys.sortedDescending().forEach { hour ->
                                            val blockEntries = dateHourMap[hour] ?: return@forEach

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .background(Color(0xFFF7F2FA), RoundedCornerShape(12.dp))
                                                    .border(BorderStroke(0.5.dp, ThBorderNeutral), RoundedCornerShape(12.dp))
                                                    .padding(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Hour block $hour",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ThPurpleDeep
                                                    )

                                                    val loggedPoints = blockEntries.filter { it.point != null }.mapNotNull { it.point }
                                                    val missingPoints = activePoints.filter { !loggedPoints.contains(it) }

                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                if (missingPoints.isEmpty()) ThSuccessBg else Color(0xFFFEF3C7),
                                                                RoundedCornerShape(8.dp)
                                                            )
                                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                                    ) {
                                                        val missingStrings = missingPoints.map { customPointNames[it] ?: String.format("%02d", it) }
                                                        Text(
                                                            text = if (missingPoints.isEmpty()) "ALL POINTS" else "MISSING: ${missingStrings.joinToString(",")}",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = if (missingPoints.isEmpty()) ThSuccessText else Color(0xFF92400E)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))

                                                blockEntries.sortedByDescending { it.timestamp }.forEach { entry ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 2.dp)
                                                            .background(Color.White, RoundedCornerShape(8.dp))
                                                            .border(BorderStroke(0.5.dp, ThBorderNeutral.copy(alpha = 0.7f)), RoundedCornerShape(8.dp))
                                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        val suffix = if (entry.outOfWindow) " ⚠️" else ""
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            if (entry.note != null) {
                                                                Text(
                                                                    text = "Note: \"${entry.note}\" at ${entry.time}$suffix",
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = ThSuccessText
                                                                )
                                                            } else {
                                                                val pointNameText = entry.point?.let { customPointNames[it] ?: String.format("%02d", it) } ?: ""
                                                                Text(
                                                                    text = "Point $pointNameText logged at ${entry.time}$suffix",
                                                                    fontSize = 12.sp,
                                                                    color = ThTextMain
                                                                )
                                                            }
                                                        }

                                                        Text(
                                                            text = "Rem",
                                                            color = ThDangerText,
                                                            fontWeight = FontWeight.Black,
                                                            fontSize = 11.sp,
                                                            modifier = Modifier
                                                                .clickable { viewModel.removeEntry(entry) }
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // F. Disclaimer Footer Notes
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Disclaimer: Local SQLite Room storage. Violations calculated while On Duty. Ensure secure intervals.\n\neagleeyetechsolu@gmail.com © 2026 | 0773554975",
                            fontSize = 10.sp,
                            color = ThTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                        )
                    }

                    1 -> { // ================= BACKUP TAB =================
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, ThBorderNeutral)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Log Maintenance & Backups",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ThPurpleDeep
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Carry out local clipboard exports or restores.",
                                    fontSize = 12.sp,
                                    color = ThTextSecondary
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                // Select & Import File Button
                                Button(
                                    onClick = { filePickerLauncher.launch("application/json") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)), // Cyan accent
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("import_file_button"),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Select & Import JSON File", fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = ThBorderNeutral.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(12.dp))

                                // Maintenance controls list structured beautifully in a reliable responsive grid
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    val jsonLiteral = viewModel.getExportString("json")
                                                    pendingExportJson = jsonLiteral
                                                    createJsonLauncher.launch("patrol_backup.json")
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("Save JSON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    val csvLiteral = viewModel.getExportString("csv")
                                                    shareFile(context, "patrol_export.csv", csvLiteral, "text/csv")
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ThSuccessText),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("Download CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.undoLastAction() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14B8A6)),
                                            enabled = canUndoState,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("Undo Action", fontSize = 11.sp)
                                        }

                                        Button(
                                            onClick = { viewModel.calculateMonthlyReport() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("Monthly Report", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.resetLog()
                                                Toast.makeText(context, "Log cleared! Temporary undo available.", Toast.LENGTH_LONG).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ThDangerText),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("Reset Log", fontSize = 11.sp)
                                        }

                                        Button(
                                            onClick = {
                                                viewModel.undoReset()
                                                Toast.makeText(context, "Restored last reset successfully!", Toast.LENGTH_SHORT).show()
                                            },
                                            enabled = canUndoReset,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("Undo Reset", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> { // ================= SETTINGS TAB =================
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, ThBorderNeutral)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "WhatsApp Alert Settings",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ThPurpleDeep
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Recipient number for WhatsApp PANIC triggers.",
                                    fontSize = 12.sp,
                                    color = ThTextSecondary
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = emergencyInput,
                                        onValueChange = { emergencyInput = it },
                                        modifier = Modifier.weight(1.2f),
                                        placeholder = { Text("+263771234567") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = ThTextMain,
                                            unfocusedTextColor = ThTextMain,
                                            focusedBorderColor = ThPurpleBrand,
                                            unfocusedBorderColor = ThBorderNeutral
                                        ),
                                        singleLine = true
                                    )
                                    Button(
                                        onClick = {
                                            viewModel.saveEmergencyNumber(emergencyInput)
                                            Toast.makeText(context, "Emergency number saved.", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ThPurpleBrand),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                Divider(color = ThBorderNeutral.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Active Patrol Points Config",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThTextMain
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Adjust the active checkpoints list below.",
                                    fontSize = 12.sp,
                                    color = ThTextSecondary
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.addPointConfig() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("add_point_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEADDFF)),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, ThBorderNeutral)
                                    ) {
                                        Text("+ Add Point", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ThPurpleBrand)
                                    }
                                    Button(
                                        onClick = { viewModel.removePointConfig() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("remove_point_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEEBEB)),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, ThBorderNeutral)
                                    ) {
                                        Text("- Remove Last", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ThDangerText)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Active codes count: ${activePoints.size}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ThTextSecondary
                                )

                                if (activePoints.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Divider(color = ThBorderNeutral.copy(alpha = 0.3f))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Custom Point Label Overrides",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ThPurpleDeep
                                    )
                                    Text(
                                        text = "Type text labels below to rename default numbering.",
                                        fontSize = 11.sp,
                                        color = ThTextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    activePoints.sorted().forEach { pNum ->
                                        val currentName = customPointNames[pNum] ?: ""
                                        var fieldVal by remember(pNum) { mutableStateOf(currentName) }

                                        LaunchedEffect(currentName) {
                                            if (fieldVal != currentName) {
                                                fieldVal = currentName
                                            }
                                        }

                                        OutlinedTextField(
                                            value = fieldVal,
                                            onValueChange = { newValue ->
                                                fieldVal = newValue
                                                viewModel.saveCustomPointName(pNum, newValue)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            label = { Text("Point $pNum Custom Name", fontSize = 11.sp) },
                                            placeholder = { Text(String.format("Point %02d", pNum), fontSize = 12.sp) },
                                            textStyle = androidx.compose.ui.text.TextStyle.Default.copy(fontSize = 13.sp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = ThTextMain,
                                                unfocusedTextColor = ThTextMain,
                                                focusedBorderColor = ThPurpleBrand,
                                                unfocusedBorderColor = ThBorderNeutral
                                             ),
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2.5. Custom Persistent Footer (Quick Actions & Brand Status)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, ThBorderNeutral.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Brand/Version & Preferred Status Label
                Column {
                    Text(
                        text = "eagleeyetechsolu@gmail.com © 2026 | 0773554975",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = ThPurpleDeep
                    )
                    Text(
                        text = if (isWindowActive) "Preferred reporting window active" else "Outside preferred window",
                        fontSize = 8.sp,
                        color = if (isWindowActive) ThSuccessText else ThTextSecondary
                    )
                }

                // Right side: Quick Action Buttons (Undo and Monthly Report)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Undo Button
                    Button(
                        onClick = {
                            viewModel.undoLastAction()
                            Toast.makeText(context, "Undid last patrol action", Toast.LENGTH_SHORT).show()
                        },
                        enabled = canUndoState,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF14B8A6),
                            disabledContainerColor = Color(0xFFE2E8F0)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("footer_undo_button")
                    ) {
                        Text(
                            text = "◀ UNDO", 
                            fontSize = 10.sp, 
                            fontWeight = FontWeight.Black,
                            color = if (canUndoState) Color.White else Color(0xFF94A3B8)
                        )
                    }

                    // Quick Monthly Report Button
                    Button(
                        onClick = {
                            viewModel.calculateMonthlyReport()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEC4899)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("footer_report_button")
                    ) {
                        Text("📊 REPORT", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }
        }

        // 3. Static Bottom Navigation Bar (Consistent across all screens)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF3EDF7))
                .border(BorderStroke(1.dp, ThBorderNeutral.copy(alpha = 0.4f)))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab 0: DASH
            val isDash = selectedTab == 0
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selectedTab = 0 }
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(54.dp)
                        .height(32.dp)
                        .background(
                            color = if (isDash) Color(0xFFEADDFF) else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📊", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "DASH",
                    fontSize = 10.sp,
                    fontWeight = if (isDash) FontWeight.Black else FontWeight.Bold,
                    color = if (isDash) Color(0xFF21005D) else Color(0xFF49454F)
                )
            }

            // Tab 1: BACKUP
            val isBackup = selectedTab == 1
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selectedTab = 1 }
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(54.dp)
                        .height(32.dp)
                        .background(
                            color = if (isBackup) Color(0xFFEADDFF) else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💾", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "BACKUP",
                    fontSize = 10.sp,
                    fontWeight = if (isBackup) FontWeight.Black else FontWeight.Bold,
                    color = if (isBackup) Color(0xFF21005D) else Color(0xFF49454F)
                )
            }

            // Tab 2: SETTINGS
            val isSettings = selectedTab == 2
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selectedTab = 2 }
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(54.dp)
                        .height(32.dp)
                        .background(
                            color = if (isSettings) Color(0xFFEADDFF) else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙️", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "SETTINGS",
                    fontSize = 10.sp,
                    fontWeight = if (isSettings) FontWeight.Black else FontWeight.Bold,
                    color = if (isSettings) Color(0xFF21005D) else Color(0xFF49454F)
                )
            }
        }
    }

    // Modal Dialog 1: Log confirmation Dialog
    activeConfirmingPoint?.let { pNum ->
        val pointNameWord = customPointNames[pNum] ?: pNum.toString()
        val msg = if (isWindowActive) {
            "Log Point $pointNameWord now?"
        } else {
            "WARNING: Outside preferred window (:10-:50).\n\nLog Point $pointNameWord anyway?"
        }

        AlertDialog(
            onDismissRequest = { activeConfirmingPoint = null },
            title = { Text("Log Code/Point Entry", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text(msg, color = if (!isWindowActive) Color(0xFFF59E0B) else Color.Unspecified) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logPoint(pNum)
                        activeConfirmingPoint = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Log")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeConfirmingPoint = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal Dialog 2: Alarm triggering state
    if (alarmActive) {
        Dialog(onDismissRequest = {}) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                border = BorderStroke(2.dp, ThDangerText)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Patrol Tracker Logo Warning",
                        modifier = Modifier
                            .size(64.dp)
                            .padding(bottom = 8.dp)
                    )

                    Text(
                        text = "⚠️ Patrol Alert Warning",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThDangerText
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = alarmMsg,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = ThTextMain
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.acceptAlarm() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)), // Diagnostic Red for Stop
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("STOP ALARM", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = { viewModel.snoozeAlarm() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)), // Amber for Snooze
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Snooze 5m", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog 3: Monthly Report popup View
    monthlyReport?.let { report ->
        Dialog(onDismissRequest = { viewModel.closeMonthlyReport() }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📊 Monthly Patrol Report",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThPurpleBrand,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    StatLineItem("Month:", report.month)
                    StatLineItem("Total patrol entries:", report.totalEntries.toString())
                    StatLineItem("Total active hours:", "${report.totalHours} hrs")
                    StatLineItem("Total missed points:", report.totalMisses.toString())
                    StatLineItem("Violations (gaps >65m):", report.violations.toString(), isHighlight = report.violations > 0)
                    StatLineItem("Best day (max active hrs):", "${report.bestDay} (${report.bestDayHours} hrs)")
                    StatLineItem("Slowest day (min active hrs):", "${report.worstDay} (${report.worstDayHours} hrs)")

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val literal = "Monthly Patrol Report\n" +
                                        "Month,\"${report.month}\"\n" +
                                        "Total Entries,\"${report.totalEntries}\"\n" +
                                        "Total Hours Active,\"${report.totalHours}\"\n" +
                                        "Total Missed Points,\"${report.totalMisses}\"\n" +
                                        "Violations,\"${report.violations}\"\n" +
                                        "Best Day,\"${report.bestDay} (${report.bestDayHours} hrs)\"\n" +
                                        "Slowest Day,\"${report.worstDay} (${report.worstDayHours} hrs)\"\n"

                                val filename = "monthly_report_${report.month.replace(" ", "_").lowercase()}.csv"
                                shareFile(context, filename, literal, "text/csv")
                            },
                            modifier = Modifier.weight(1.3f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Share CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.closeMonthlyReport() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ThTextSecondary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Close", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// Map Helper logic (used for UI state parsing)
private fun _addEntryToMap(
    map: MutableMap<String, MutableMap<Int, MutableList<PatrolEntry>>>,
    e: PatrolEntry
) {
    val dateLabel = e.date
    val itemCal = Calendar.getInstance().apply { timeInMillis = e.timestamp }
    val hour = itemCal.get(Calendar.HOUR_OF_DAY)

    val dateHourMap = map.getOrPut(dateLabel) { mutableMapOf() }
    val hourList = dateHourMap.getOrPut(hour) { mutableListOf() }
    hourList.add(e)
}

// FlowRow layout component for side-by-side maintenance action grids
@Composable
fun FlowRowLayout(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = 6.dp,
    content: List<@Composable () -> Unit>
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val pairs = content.chunked(2)
        pairs.forEach { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing / 2),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                pair.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
                        item()
                    }
                }
                if (pair.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun StatLineItem(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = ThTextSecondary)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighlight) ThDangerText else ThPurpleBrand
        )
    }
}

fun shareFile(context: Context, filename: String, content: String, mimeType: String) {
    try {
        val cacheFile = java.io.File(context.cacheDir, filename)
        cacheFile.writeText(content)
        val fileUri: Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, filename)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export $filename"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error exporting file: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
