package com.example.ui

import android.app.Application
import android.content.Context
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class PatrolViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase(application)
    private val repository = PatrolRepository(database)
    val prefs = PatrolPreferences(application)

    private val appContext: Context by lazy {
        val app = getApplication<Application>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                app.createAttributionContext("default")
            } catch (e: Exception) {
                app
            }
        } else {
            app
        }
    }

    val patrolEntries = MutableStateFlow<List<PatrolEntry>>(emptyList())
    val isOnDuty = MutableStateFlow(prefs.isOnDuty)
    val customCheckpoints = MutableStateFlow<List<String>>(prefs.getCustomCheckpoints())

    val timerTick = MutableStateFlow(System.currentTimeMillis())
    val nextDeadlineTime = MutableStateFlow(0L)
    val secondsRemaining = MutableStateFlow(0L)
    val missingPoints = MutableStateFlow<List<String>>(emptyList())

    // Alarm state
    val alarmSoundActive = MutableStateFlow<String?>(null)
    private var currentRingtone: android.media.Ringtone? = null
    private var lastAlarmMinuteTriggered = -1

    // Compliance report
    val complianceReport = MutableStateFlow<ComplianceReport?>(null)

    // Undo structure
    private var lastUndoAction: UndoAction? = null
    val canUndo = MutableStateFlow(false)

    private var timerJob: Job? = null

    init {
        loadEntries()
        startTimer()
    }

    fun loadEntries() {
        viewModelScope.launch {
            val list = repository.getAllEntries()
            patrolEntries.value = list
            updateDeadlineAndMissing(list)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                timerTick.value = now
                if (isOnDuty.value) {
                    if (nextDeadlineTime.value == 0L) {
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = now
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        nextDeadlineTime.value = cal.timeInMillis + (60 * 60 * 1000L)
                    }

                    val remaining = ((nextDeadlineTime.value - now) / 1000).coerceAtLeast(0)
                    secondsRemaining.value = remaining

                    // Check if deadline missed
                    if (now >= nextDeadlineTime.value) {
                        handleMissedDeadline()
                    }

                    // Sound alarm only at :10 past and :50 of the hour if points are missed
                    checkPeriodicMissedAlarm(now)
                } else {
                    nextDeadlineTime.value = 0L
                    secondsRemaining.value = 0L
                    missingPoints.value = emptyList()
                    lastAlarmMinuteTriggered = -1
                }
                delay(1000)
            }
        }
    }

    private fun checkPeriodicMissedAlarm(now: Long) {
        if (!isOnDuty.value) {
            lastAlarmMinuteTriggered = -1
            return
        }
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val minute = cal.get(Calendar.MINUTE)
        
        if (minute == 10 || minute == 50) {
            if (lastAlarmMinuteTriggered != minute) {
                // Get start of the current hour
                val currentHourCal = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val hourStartMs = currentHourCal.timeInMillis
                
                // Find visited checkpoints in the current hour
                val visitedInCurrentHour = patrolEntries.value
                    .filter { it.status == "CHECKPOINT" && it.timestamp >= hourStartMs }
                    .map { it.checkpoint.trim().lowercase() }
                    .toSet()
                
                // Are there any custom checkpoints not yet visited?
                val missingInCurrentHour = customCheckpoints.value.any { it.trim().lowercase() !in visitedInCurrentHour }
                
                // Also check if any missed checkpoints are logged during the current shift
                val startLog = patrolEntries.value.firstOrNull { it.status == "START" }
                val startTimestamp = startLog?.timestamp ?: 0L
                val hasLoggedMissedInShift = patrolEntries.value.any { it.status == "MISSED" && it.timestamp >= startTimestamp }
                
                if (missingInCurrentHour || hasLoggedMissedInShift) {
                    playSoundAlarm()
                    triggerViolationVibration()
                    lastAlarmMinuteTriggered = minute
                }
            }
        } else {
            lastAlarmMinuteTriggered = -1
        }
    }

    private suspend fun handleMissedDeadline() {
        val now = System.currentTimeMillis()
        val hourMs = 60 * 60 * 1000L
        
        // Find which checkpoints have NOT been logged as "CHECKPOINT" in this interval slot
        val allPoints = customCheckpoints.value
        val slotStart = nextDeadlineTime.value - hourMs
        val slotEnd = nextDeadlineTime.value

        val loggedInSlot = patrolEntries.value
            .filter { it.status == "CHECKPOINT" && it.timestamp in slotStart until slotEnd }
            .map { it.checkpoint.trim().lowercase() }
            .toSet()

        val missed = allPoints.filter { it.trim().lowercase() !in loggedInSlot }

        if (missed.isNotEmpty()) {
            val startHourStr = java.text.SimpleDateFormat("HH:00", java.util.Locale.getDefault()).format(java.util.Date(slotStart))
            val endHourStr = java.text.SimpleDateFormat("HH:00", java.util.Locale.getDefault()).format(java.util.Date(slotEnd))
            val timeRange = "$startHourStr - $endHourStr"
            val missedEntries = missed.map { name ->
                PatrolEntry(
                    checkpoint = name,
                    timestamp = slotEnd,
                    status = "MISSED",
                    notes = "Automatically registered missed status for Hour Slot $timeRange"
                )
            }
            repository.insertAll(missedEntries)
            triggerViolationVibration()
        }

        // Set nextDeadlineTime to the next top of the hour
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        nextDeadlineTime.value = cal.timeInMillis + hourMs
        loadEntries()
    }

    fun addCheckpoint(name: String, context: Context) {
        if (name.isBlank()) return
        val trimmed = name.trim()
        val current = customCheckpoints.value.toMutableList()
        if (current.any { it.equals(trimmed, ignoreCase = true) }) {
            Toast.makeText(context, "Checkpoint already exists!", Toast.LENGTH_SHORT).show()
            return
        }
        current.add(trimmed)
        customCheckpoints.value = current
        prefs.saveCustomCheckpoints(current)
        Toast.makeText(context, "Checkpoint appended!", Toast.LENGTH_SHORT).show()
        // Update live stats immediately
        loadEntries()
    }

    fun removeCheckpoint(name: String, context: Context) {
        val current = customCheckpoints.value.toMutableList()
        if (current.remove(name)) {
            customCheckpoints.value = current
            prefs.saveCustomCheckpoints(current)
            Toast.makeText(context, "Checkpoint removed!", Toast.LENGTH_SHORT).show()
            // Update live stats immediately
            loadEntries()
        }
    }

    fun toggleDuty(context: Context) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (isOnDuty.value) {
                // End duty
                isOnDuty.value = false
                prefs.isOnDuty = false
                stopAlarm()
                val entry = PatrolEntry(
                    checkpoint = "Duty Session Ended",
                    timestamp = now,
                    status = "FINISH",
                    notes = "Finished secure shift"
                )
                repository.insert(entry)
                nextDeadlineTime.value = 0L
                secondsRemaining.value = 0L
                missingPoints.value = emptyList()
            } else {
                // Start duty
                isOnDuty.value = true
                prefs.isOnDuty = true
                val entry = PatrolEntry(
                    checkpoint = "Duty Session Activated",
                    timestamp = now,
                    status = "START",
                    notes = "Patrol guards on standby details active"
                )
                repository.insert(entry)
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                nextDeadlineTime.value = cal.timeInMillis + (60 * 60 * 1000L)
            }
            loadEntries()
        }
    }

    fun canLogCheckpoint(name: String): Boolean {
        if (name.isBlank()) return true
        val entries = patrolEntries.value
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentHourStart = cal.timeInMillis
        val currentHourEnd = currentHourStart + (60 * 60 * 1000L)

        return entries.none { e ->
            e.status == "CHECKPOINT" && 
            e.checkpoint.trim().equals(name.trim(), ignoreCase = true) && 
            e.timestamp in currentHourStart until currentHourEnd
        }
    }

    fun logCheckpoint(name: String, notes: String) {
        if (!canLogCheckpoint(name)) return
        stopAlarm()
        viewModelScope.launch {
            val entry = PatrolEntry(
                checkpoint = name.trim(),
                timestamp = System.currentTimeMillis(),
                status = "CHECKPOINT",
                notes = notes
            )
            repository.insert(entry)
            // Save state for undo
            lastUndoAction = UndoAction.Add(entry)
            canUndo.value = true
            loadEntries()
        }
    }

    fun logEmergency(category: String, notes: String) {
        viewModelScope.launch {
            val entry = PatrolEntry(
                checkpoint = "🚨 EMERGENCY: ${category.uppercase()}",
                timestamp = System.currentTimeMillis(),
                status = "EMERGENCY",
                notes = notes
            )
            repository.insert(entry)
            playSoundAlarm()
            triggerEmergencyVibration()
            loadEntries()
        }
    }

    fun deleteLog(id: Int) {
        viewModelScope.launch {
            val list = patrolEntries.value
            val match = list.find { it.id == id }
            if (match != null) {
                repository.deleteById(id)
                lastUndoAction = UndoAction.Delete(match)
                canUndo.value = true
                loadEntries()
            }
        }
    }

    fun undoLastAction() {
        val action = lastUndoAction ?: return
        viewModelScope.launch {
            when (action) {
                is UndoAction.Delete -> {
                    repository.insert(action.entry)
                }
                is UndoAction.Add -> {
                    val list = repository.getAllEntries()
                    val match = list.find { 
                        it.timestamp == action.entry.timestamp && 
                        it.checkpoint == action.entry.checkpoint && 
                        it.status == action.entry.status 
                    }
                    if (match != null) {
                        repository.deleteById(match.id)
                    }
                }
                is UndoAction.Reset -> {
                    repository.insertAll(action.entries)
                }
            }
            lastUndoAction = null
            canUndo.value = false
            loadEntries()
        }
    }

    fun resetLogs() {
        viewModelScope.launch {
            val currentList = repository.getAllEntries()
            if (currentList.isNotEmpty()) {
                lastUndoAction = UndoAction.Reset(currentList)
                canUndo.value = true
                repository.clearAll()
                loadEntries()
            }
        }
    }

    private fun updateDeadlineAndMissing(list: List<PatrolEntry>) {
        if (!isOnDuty.value) return
        val now = System.currentTimeMillis()
        val hourMs = 60 * 60 * 1000L

        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetDeadline = cal.timeInMillis + hourMs
        nextDeadlineTime.value = targetDeadline

        // Determine missing points for active slot (current hour)
        val slotStart = cal.timeInMillis
        val loggedInSlot = list
            .filter { it.status == "CHECKPOINT" && it.timestamp in slotStart until targetDeadline }
            .map { it.checkpoint.trim().lowercase() }
            .toSet()

        missingPoints.value = customCheckpoints.value.filter { it.trim().lowercase() !in loggedInSlot }
    }

    fun isPreferredActive(): Boolean {
        val cal = Calendar.getInstance()
        val minute = cal.get(Calendar.MINUTE)
        return minute in 10..50
    }

    fun playSoundAlarm() {
        try {
            currentRingtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(appContext, uri)
            ringtone?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    try {
                        it.volume = prefs.alarmVolume / 100f
                    } catch (ex: Exception) {
                        // ignore
                    }
                }
                it.play()
                currentRingtone = it
                alarmSoundActive.value = "Security Duty Guard Alarm"
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun stopAlarm() {
        try {
            currentRingtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
            currentRingtone = null
            alarmSoundActive.value = null
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun triggerViolationVibration() {
        try {
            val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 300, 150, 300)
                val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun triggerEmergencyVibration() {
        try {
            val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 500, 200, 500, 200, 1000)
                val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(1000)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun logObservation(notes: String) {
        if (notes.isBlank()) return
        viewModelScope.launch {
            val entry = PatrolEntry(
                checkpoint = "Observation Note",
                timestamp = System.currentTimeMillis(),
                status = "OBSERVATION",
                notes = notes.trim()
            )
            repository.insert(entry)
            loadEntries()
        }
    }

    fun calculateMonthlyReport(): ComplianceReport {
        val list = patrolEntries.value.filter { it.status != "OBSERVATION" }
        val totalLogs = list.size
        val checkpoints = list.filter { it.status == "CHECKPOINT" }.size
        val missed = list.filter { it.status == "MISSED" }.size
        val starts = list.filter { it.status == "START" }.size
        val emergencies = list.filter { it.status == "EMERGENCY" }.size

        val denominator = checkpoints + missed
        val complianceRate = if (denominator > 0) {
            ((checkpoints.toFloat() / denominator.toFloat()) * 100).toInt()
        } else {
            100
        }

        val report = ComplianceReport(
            totalLogs = totalLogs,
            totalCheckpoints = checkpoints,
            totalMissed = missed,
            totalStarts = starts,
            totalEmergencies = emergencies,
            compliancePercent = complianceRate
        )
        complianceReport.value = report
        return report
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            if (ch == '"') {
                inQuotes = !inQuotes
            } else if (ch == ',' && !inQuotes) {
                tokens.add(cur.toString())
                cur.setLength(0)
            } else {
                cur.append(ch)
            }
        }
        tokens.add(cur.toString())
        return tokens
    }

    fun restoreFromJson(
        jsonString: String,
        onError: (String) -> Unit,
        onSuccess: (insertedCount: Int, skippedCount: Int) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val cleanedJson = jsonString.trim()
                
                // 1. Detect and parse CSV format
                if (!cleanedJson.startsWith("{") && !cleanedJson.startsWith("[")) {
                    val lines = cleanedJson.lines()
                    if (lines.isNotEmpty() && lines[0].contains("Point", ignoreCase = true)) {
                        val existingEntries = repository.getAllEntries()
                        val entriesToInsert = mutableListOf<PatrolEntry>()
                        var skipped = 0
                        
                        for (idx in 1 until lines.size) {
                            val line = lines[idx].trim()
                            if (line.isEmpty()) continue
                            
                            val tokens = parseCsvLine(line)
                            if (tokens.size >= 5) {
                                val checkpointVal = tokens[2].replace("\"", "").trim()
                                val noteVal = tokens[3].replace("\"", "").trim()
                                val tsString = tokens[4].replace("\"", "").trim()
                                val timestamp = tsString.toLongOrNull() ?: continue
                                val checkpoint = if (checkpointVal.isEmpty()) "Checkpoint" else checkpointVal
                                
                                val isDuplicate = existingEntries.any { existing ->
                                    existing.timestamp == timestamp &&
                                    existing.checkpoint.trim().equals(checkpoint.trim(), ignoreCase = true)
                                }
                                
                                if (isDuplicate) {
                                    skipped++
                                } else {
                                    entriesToInsert.add(
                                        PatrolEntry(
                                            checkpoint = checkpoint,
                                            timestamp = timestamp,
                                            status = if (noteVal.isNotEmpty() && checkpointVal.isEmpty()) "NOTE" else "CHECKPOINT",
                                            notes = noteVal
                                        )
                                    )
                                }
                            }
                        }
                        
                        if (entriesToInsert.isNotEmpty()) {
                            repository.insertAll(entriesToInsert)
                        }
                        loadEntries()
                        onSuccess(entriesToInsert.size, skipped)
                        return@launch
                    }
                }

                // 2. Parse JSON format (either direct Array or root Object)
                val isObject = cleanedJson.startsWith("{")
                
                // Restore configurations if it is an object
                if (isObject) {
                    val rootObj = JSONObject(cleanedJson)
                    if (rootObj.has("emergencyNumber")) {
                        prefs.emergencyNumber = rootObj.getString("emergencyNumber")
                    } else if (rootObj.has("emergency_number")) {
                        prefs.emergencyNumber = rootObj.getString("emergency_number")
                    }
                    if (rootObj.has("patrolInterval")) {
                        prefs.patrolInterval = rootObj.optInt("patrolInterval", 15)
                    } else if (rootObj.has("patrol_interval")) {
                        prefs.patrolInterval = rootObj.optInt("patrol_interval", 15)
                    }
                    if (rootObj.has("startHour")) {
                        prefs.startHour = rootObj.optInt("startHour", 20)
                    }
                    if (rootObj.has("endHour")) {
                        prefs.endHour = rootObj.optInt("endHour", 6)
                    }
                    if (rootObj.has("offDuty")) {
                        isOnDuty.value = !rootObj.optBoolean("offDuty", true)
                        prefs.isOnDuty = isOnDuty.value
                    }
                    
                    val pointsArray = rootObj.optJSONArray("points") ?: rootObj.optJSONArray("customCheckpoints") ?: rootObj.optJSONArray("custom_check_points")
                    if (pointsArray != null) {
                        val importedPoints = mutableListOf<String>()
                        for (i in 0 until pointsArray.length()) {
                            val p = pointsArray.opt(i)
                            if (p is Number) {
                                importedPoints.add("Point $p")
                            } else if (p != null) {
                                importedPoints.add(p.toString())
                            }
                        }
                        if (importedPoints.isNotEmpty()) {
                            customCheckpoints.value = importedPoints
                            prefs.saveCustomCheckpoints(importedPoints)
                        }
                    }
                }

                // Get patrols array
                val jsonArray = if (isObject) {
                    val rootObj = JSONObject(cleanedJson)
                    rootObj.optJSONArray("patrols")
                        ?: rootObj.optJSONArray("patrolEntries")
                        ?: rootObj.optJSONArray("entries")
                        ?: rootObj.optJSONArray("data")
                        ?: JSONArray()
                } else {
                    JSONArray(cleanedJson)
                }

                val existingEntries = repository.getAllEntries()
                val entriesToInsert = mutableListOf<PatrolEntry>()
                var skipped = 0

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    
                    val pointObj = obj.opt("point")
                    val checkpoint = obj.optString("checkpoint", 
                        if (pointObj != null && pointObj != JSONObject.NULL) {
                            if (pointObj is Number) "Point $pointObj" else pointObj.toString()
                        } else {
                            "Checkpoint"
                        }
                    )
                    
                    val timestamp = obj.getLong("timestamp")
                    val status = obj.optString("status", "CHECKPOINT")
                    val notes = obj.optString("notes", obj.optString("note", ""))

                    val isDuplicate = existingEntries.any { existing ->
                        existing.timestamp == timestamp &&
                        existing.checkpoint.trim().equals(checkpoint.trim(), ignoreCase = true) &&
                        existing.status == status
                    }

                    if (isDuplicate) {
                        skipped++
                    } else {
                        entriesToInsert.add(
                            PatrolEntry(
                                checkpoint = checkpoint,
                                timestamp = timestamp,
                                status = status,
                                notes = notes
                            )
                        )
                    }
                }

                if (entriesToInsert.isNotEmpty()) {
                    repository.insertAll(entriesToInsert)
                }
                loadEntries()
                onSuccess(entriesToInsert.size, skipped)
            } catch (e: Exception) {
                onError(e.message ?: "Invalid formatting or fields")
            }
        }
    }

    fun getExportString(): String {
        val root = JSONObject()
        root.put("version", "5.5")
        root.put("exported", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()))
        root.put("offDuty", !isOnDuty.value)
        root.put("emergencyNumber", prefs.emergencyNumber)
        root.put("patrolInterval", prefs.patrolInterval)
        root.put("startHour", prefs.startHour)
        root.put("endHour", prefs.endHour)
        
        val pointsArr = JSONArray()
        customCheckpoints.value.forEach { pointsArr.put(it) }
        root.put("points", pointsArr)
        
        val array = JSONArray()
        patrolEntries.value.forEach {
            val obj = JSONObject()
            obj.put("checkpoint", it.checkpoint)
            
            val cleanNum = it.checkpoint.replace("Point ", "").trim().toIntOrNull()
            if (cleanNum != null) {
                obj.put("point", cleanNum)
            } else {
                obj.put("point", it.checkpoint)
            }
            obj.put("timestamp", it.timestamp)
            obj.put("status", it.status)
            obj.put("notes", it.notes)
            obj.put("note", it.notes)
            array.put(obj)
        }
        root.put("patrols", array)
        
        return root.toString(4)
    }
}

sealed class UndoAction {
    data class Add(val entry: PatrolEntry) : UndoAction()
    data class Delete(val entry: PatrolEntry) : UndoAction()
    data class Reset(val entries: List<PatrolEntry>) : UndoAction()
}

data class ComplianceReport(
    val totalLogs: Int,
    val totalCheckpoints: Int,
    val totalMissed: Int,
    val totalStarts: Int,
    val totalEmergencies: Int,
    val compliancePercent: Int
)
