package com.example.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@JsonClass(generateAdapter = true)
data class BackupEntry(
    val date: String,
    val dateISO: String,
    val time: String,
    val point: Int?,
    val note: String?,
    val timestamp: Long,
    val outOfWindow: Boolean
)

@JsonClass(generateAdapter = true)
data class PatrolBackup(
    val version: String,
    val exported: String,
    val points: List<Int>,
    val patrols: List<BackupEntry>,
    val offDuty: Boolean,
    val emergencyNumber: String,
    val dutyStartTime: Long
)

sealed class UndoAction {
    data class Add(val entry: PatrolEntry) : UndoAction()
    data class Delete(val entry: PatrolEntry) : UndoAction()
    data class Reset(val entries: List<PatrolEntry>) : UndoAction()
}

data class ViolationItem(
    val date: String,
    val hour: Int,
    val type: String,
    val detail: String
)

data class PatrolStats(
    val totalViolations: Int,
    val lastViolations: List<ViolationItem>,
    val totalMissedPoints: Int,
    val hoursWorkedToday: Int,
    val hoursWorkedThisWeek: Int,
    val hoursWorkedThisMonth: Int,
    val lastDayShiftHours: Int,
    val lastNightShiftHours: Int
)

data class MonthlyReport(
    val month: String,
    val totalEntries: Int,
    val totalHours: Int,
    val totalMisses: Int,
    val violations: Int,
    val bestDay: String,
    val bestDayHours: Int,
    val worstDay: String,
    val worstDayHours: Int
)

class PatrolViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = PatrolRepository(database.patrolDao(), PatrolPreferences(application))

    private val moshi = Moshi.Builder().build()
    private val backupAdapter = moshi.adapter(PatrolBackup::class.java)

    // Observables from Preferences
    val offDuty = MutableStateFlow(repository.preferences.offDuty)
    val emergencyNumber = MutableStateFlow(repository.preferences.emergencyNumber)
    val points = MutableStateFlow(repository.preferences.patrolPoints)
    val dutyStartTime = MutableStateFlow(repository.preferences.dutyStartTime)
    val customPointNames = MutableStateFlow(repository.preferences.customPointNames)
    private var reminderTriggeredForDeadline: Long = 0L

    // Live entries
    val allEntries = repository.allEntriesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Alarm States
    val alarmActive = MutableStateFlow(false)
    val alarmMessage = MutableStateFlow("")
    private var toneGenerator: ToneGenerator? = null
    private var alarmJob: Job? = null

    // Undo structure
    private var lastAction: UndoAction? = null
    val canUndo = MutableStateFlow(false)

    // Reset log backup structure
    private var resetBackupList = mutableListOf<PatrolEntry>()
    val canUndoReset = MutableStateFlow(false)

    // Dialog overlays
    val monthlyReportData = MutableStateFlow<MonthlyReport?>(null)

    // Timer variables
    val countdownText = MutableStateFlow("Time left: --")
    val deadlineText = MutableStateFlow("Next patrol deadline: none")
    val hourIndicatorText = MutableStateFlow("System Off Duty")
    val windowStatusText = MutableStateFlow("Window status: OFF DUTY")
    val isWindowActive = MutableStateFlow(false)

    private var timerJob: Job? = null
    private var deadlineCheckJob: Job? = null
    private var deadlineAlarmTriggered = false
    private var lastCheckedHour = -1

    init {
        // Start timers
        startTimerLoop()
        startDeadlineCheckLoop()
    }

    // Toggle duty status
    fun setOnDuty() {
        offDuty.value = false
        repository.preferences.offDuty = false
        val now = System.currentTimeMillis()
        dutyStartTime.value = now
        repository.preferences.dutyStartTime = now
        reminderTriggeredForDeadline = 0L
        lastCheckedHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        startTimerLoop()
    }

    fun setOffDuty() {
        offDuty.value = true
        repository.preferences.offDuty = true
        dutyStartTime.value = 0L
        repository.preferences.dutyStartTime = 0L
        reminderTriggeredForDeadline = 0L
        lastCheckedHour = -1
        stopTimerLoop()
        closeAlarm()
    }

    // Settings actions
    fun saveEmergencyNumber(number: String) {
        emergencyNumber.value = number
        repository.preferences.emergencyNumber = number
    }

    fun saveCustomPointName(pointNum: Int, name: String) {
        val updated = customPointNames.value.toMutableMap()
        if (name.isBlank()) {
            updated.remove(pointNum)
        } else {
            updated[pointNum] = name
        }
        customPointNames.value = updated
        repository.preferences.customPointNames = updated
    }

    fun setPoints(newPoints: List<Int>) {
        points.value = newPoints
        repository.preferences.patrolPoints = newPoints
    }

    fun addPointConfig() {
        val next = (points.value.maxOrNull() ?: 0) + 1
        val updated = points.value + next
        setPoints(updated)
    }

    fun removePointConfig() {
        if (points.value.isNotEmpty()) {
            val updated = points.value.dropLast(1)
            setPoints(updated)
        }
    }

    // Undo actions
    private fun setLastAction(action: UndoAction) {
        lastAction = action
        canUndo.value = true
    }

    private fun clearLastAction() {
        lastAction = null
        canUndo.value = false
    }

    fun undoLastAction() {
        val action = lastAction ?: return
        viewModelScope.launch {
            when (action) {
                is UndoAction.Add -> {
                    repository.deleteEntryById(action.entry.id)
                }
                is UndoAction.Delete -> {
                    repository.insertEntry(action.entry.copy(id = 0)) // insert fresh
                }
                is UndoAction.Reset -> {
                    repository.insertEntries(action.entries)
                    canUndoReset.value = false
                    resetBackupList.clear()
                }
            }
            clearLastAction()
        }
    }

    fun resetLog() {
        viewModelScope.launch {
            val currentEntries = repository.getAllEntries()
            if (currentEntries.isNotEmpty()) {
                resetBackupList.clear()
                resetBackupList.addAll(currentEntries)
                canUndoReset.value = true
                repository.clearAllEntries()
                setLastAction(UndoAction.Reset(currentEntries))
            }
        }
    }

    fun undoReset() {
        if (canUndoReset.value && resetBackupList.isNotEmpty()) {
            viewModelScope.launch {
                repository.insertEntries(resetBackupList)
                canUndoReset.value = false
                resetBackupList.clear()
                clearLastAction()
            }
        }
    }

    // Logging actions
    fun logPoint(pointNum: Int) {
        if (offDuty.value) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val entry = createEntryObject(pointNum, null, now)
            val id = repository.insertEntry(entry)
            setLastAction(UndoAction.Add(entry.copy(id = id)))
        }
    }

    fun logCustomNote(note: String) {
        if (note.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val entry = createEntryObject(null, note, now)
            val id = repository.insertEntry(entry)
            setLastAction(UndoAction.Add(entry.copy(id = id)))
        }
    }

    fun removeEntry(entry: PatrolEntry) {
        viewModelScope.launch {
            repository.deleteEntry(entry)
            setLastAction(UndoAction.Delete(entry))
        }
    }

    // Helpers
    private fun isPreferredWindow(minutes: Int): Boolean {
        return minutes in 10..50
    }

    private fun createEntryObject(point: Int?, note: String?, timestamp: Long): PatrolEntry {
        val d = Date(timestamp)
        val dateISO = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(d)
        
        // Custom descriptive weekday and date matching local browser
        val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(d)
        
        val cal = Calendar.getInstance()
        cal.time = d
        val minutes = cal.get(Calendar.MINUTE)
        val inPref = isPreferredWindow(minutes)

        return PatrolEntry(
            date = dateStr,
            dateISO = dateISO,
            time = timeStr,
            point = point,
            note = note,
            timestamp = timestamp,
            outOfWindow = !inPref
        )
    }

    // Timer checking
    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val cal = Calendar.getInstance()
                cal.timeInMillis = now
                val minutes = cal.get(Calendar.MINUTE)
                val seconds = cal.get(Calendar.SECOND)

                // Window status
                if (offDuty.value) {
                    windowStatusText.value = "Window status: OFF DUTY"
                    isWindowActive.value = false
                    hourIndicatorText.value = "System Off Duty"
                    countdownText.value = ""
                    deadlineText.value = "Next patrol deadline: none"
                } else {
                    isWindowActive.value = isPreferredWindow(minutes)
                    windowStatusText.value = if (isWindowActive.value) {
                        "Window status: ACTIVE :10-:50"
                    } else {
                        "Window status: OUTSIDE :10-:50"
                    }

                    // Calculate hour block countdown
                    val currentHour = cal.get(Calendar.HOUR_OF_DAY)
                    val nextHourStart = (cal.clone() as Calendar).apply {
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        add(Calendar.HOUR_OF_DAY, 1)
                    }.timeInMillis

                    val diffHour = nextHourStart - now
                    val currentHourNum = currentHour

                    // Query missing points for this current hour in memory
                    val currentEntries = allEntries.value
                    val loggedInHour = currentEntries.filter {
                        val entryCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                        it.point != null &&
                                entryCal.get(Calendar.HOUR_OF_DAY) == currentHourNum &&
                                isSameDay(entryCal, cal) &&
                                (dutyStartTime.value == 0L || it.timestamp >= dutyStartTime.value)
                    }.mapNotNull { it.point }

                    val missing = points.value.filter { !loggedInHour.contains(it) }
                    if (missing.isEmpty()) {
                        hourIndicatorText.value = "All points tagged for this hour."
                    } else {
                        val minLeft = diffHour / 60000
                        val secLeft = (diffHour % 60000) / 1000
                        val missingStrings = missing.map { customPointNames.value[it] ?: String.format("%02d", it) }
                        hourIndicatorText.value = "Time left in hour: ${minLeft}m ${secLeft}s (Missing: ${missingStrings.joinToString(", ")})"
                    }

                    // Update next deadline countdown based on last point entry within this turn/shift
                    val lastPointEntry = currentEntries.firstOrNull { 
                        it.point != null && (dutyStartTime.value == 0L || it.timestamp >= dutyStartTime.value)
                    }
                    val deadline = if (lastPointEntry != null) {
                        lastPointEntry.timestamp + 60 * 60 * 1000
                    } else {
                        dutyStartTime.value + 60 * 60 * 1000
                    }

                    if (dutyStartTime.value == 0L) {
                        deadlineText.value = "Next patrol deadline: none"
                        countdownText.value = ""
                    } else {
                        val diffDeadline = deadline - now
                        val deadlineDate = Date(deadline)
                        val deadlineStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(deadlineDate)
                        deadlineText.value = "Next patrol deadline: $deadlineStr"

                        if (diffDeadline <= 0) {
                            countdownText.value = "Deadline passed!"
                        } else {
                            val dMins = diffDeadline / 60000
                            val dSecs = (diffDeadline % 60000) / 1000
                            countdownText.value = "Time left: ${dMins}m ${dSecs}s"

                            // Alarm alert reminder 10 mins before time elapse
                            if (diffDeadline <= 10 * 60 * 1000) {
                                val trackingId = lastPointEntry?.timestamp ?: dutyStartTime.value
                                if (missing.isNotEmpty() && reminderTriggeredForDeadline != trackingId) {
                                    reminderTriggeredForDeadline = trackingId
                                    triggerReminderAlarm(10)
                                }
                            }
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopTimerLoop() {
        timerJob?.cancel()
        timerJob = null
        countdownText.value = ""
        deadlineText.value = "Next patrol deadline: none"
        hourIndicatorText.value = "System Off Duty"
        windowStatusText.value = "Window status: OFF DUTY"
        isWindowActive.value = false
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun startDeadlineCheckLoop() {
        deadlineCheckJob?.cancel()
        deadlineCheckJob = viewModelScope.launch {
            while (true) {
                if (!offDuty.value && !alarmActive.value) {
                    val cal = Calendar.getInstance()
                    val currentHour = cal.get(Calendar.HOUR_OF_DAY)
                    if (lastCheckedHour != -1 && currentHour != lastCheckedHour) {
                        val hourToCheck = lastCheckedHour
                        lastCheckedHour = currentHour
                        checkPastHourDeadline(hourToCheck)
                    } else if (lastCheckedHour == -1) {
                        lastCheckedHour = currentHour
                    }
                }
                delay(1000)
            }
        }
    }

    private suspend fun checkPastHourDeadline(prevHour: Int) {
        val now = Calendar.getInstance()
        val prevCal = (now.clone() as Calendar).apply {
            val currentHour = get(Calendar.HOUR_OF_DAY)
            set(Calendar.HOUR_OF_DAY, prevHour)
            if (prevHour > currentHour) {
                // If previous hour is greater than current, it was yesterday
                add(Calendar.DAY_OF_YEAR, -1)
            }
        }

        val currentEntries = repository.getAllEntries()
        val loggedInHour = currentEntries.filter {
            val entryCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            it.point != null &&
                    entryCal.get(Calendar.HOUR_OF_DAY) == prevHour &&
                    isSameDay(entryCal, prevCal) &&
                    (dutyStartTime.value == 0L || it.timestamp >= dutyStartTime.value)
        }.mapNotNull { it.point }

        val missing = points.value.filter { !loggedInHour.contains(it) }
        if (missing.isNotEmpty()) {
            triggerAlarm(missing, prevHour)
        }
    }

    private fun triggerAlarm(missing: List<Int>, hour: Int) {
        val missingStrings = missing.map { customPointNames.value[it] ?: String.format("%02d", it) }
        alarmMessage.value = "Hour $hour ended. Missing: ${missingStrings.joinToString(", ")}"
        alarmActive.value = true
        startBeepingAndVibrating()
    }

    private fun triggerReminderAlarm(mLeft: Int) {
        alarmMessage.value = "⚠️ Reminder: Only $mLeft minutes left before patrol time elapses!"
        alarmActive.value = true
        startBeepingAndVibrating()
    }

    private fun startBeepingAndVibrating() {
        alarmJob?.cancel()
        alarmJob = viewModelScope.launch {
            val context = getApplication<Application>()
            while (alarmActive.value) {
                // Beep
                try {
                    if (toneGenerator == null) {
                        toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                    }
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 500)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Vibrate
                try {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            it.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            it.vibrate(500)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(2000)
            }
        }
    }

    fun snoozeAlarm() {
        closeAlarm()
        // Snooze 5 minutes, then trigger check again
        viewModelScope.launch {
            delay(5 * 60 * 1000L)
            if (!offDuty.value) {
                // Check if still missing
                val now = Calendar.getInstance()
                val prevCal = (now.clone() as Calendar).apply {
                    if (now.get(Calendar.MINUTE) < 5) add(Calendar.HOUR_OF_DAY, -1)
                }
                val prevHour = prevCal.get(Calendar.HOUR_OF_DAY)

                val currentEntries = repository.getAllEntries()
                val loggedInHour = currentEntries.filter {
                    val entryCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                    it.point != null &&
                            entryCal.get(Calendar.HOUR_OF_DAY) == prevHour &&
                            isSameDay(entryCal, prevCal) &&
                            (dutyStartTime.value == 0L || it.timestamp >= dutyStartTime.value)
                }.mapNotNull { it.point }

                val missing = points.value.filter { !loggedInHour.contains(it) }
                if (missing.isNotEmpty()) {
                    triggerAlarm(missing, prevHour)
                }
            }
        }
    }

    fun acceptAlarm() {
        closeAlarm()
    }

    private fun closeAlarm() {
        alarmActive.value = false
        alarmJob?.cancel()
        alarmJob = null
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            val context = getApplication<Application>()
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Reactive Stats State Computation
    val statsState: StateFlow<PatrolStats> = combine(allEntries, points, dutyStartTime, offDuty) { entries, activePoints, dutyStart, isOff ->
        if (entries.isEmpty() || isOff) {
            return@combine PatrolStats(0, emptyList(), 0, 0, 0, 0, 0, 0)
        }

        // Limit tracking state since dutyStartTime if set
        val onDutyEntries = entries.filter { dutyStart == 0L || it.timestamp >= dutyStart }

        if (onDutyEntries.isEmpty()) {
            return@combine PatrolStats(0, emptyList(), 0, 0, 0, 0, 0, 0)
        }

        // 1. Group by ISO Date and Hour
        val grouped = mutableMapOf<String, MutableMap<Int, MutableList<PatrolEntry>>>()
        onDutyEntries.forEach { entry ->
            val d = Date(entry.timestamp)
            val dateKey = entry.dateISO
            val cal = Calendar.getInstance().apply { time = d }
            val hour = cal.get(Calendar.HOUR_OF_DAY)

            grouped.getOrPut(dateKey) { mutableMapOf() }
                .getOrPut(hour) { mutableListOf() }
                .add(entry)
        }

        val violations = mutableListOf<ViolationItem>()

        // Find missing violations in past completed hour blocks
        val nowCal = Calendar.getInstance()
        val todayISO = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(nowCal.time)
        val currentHour = nowCal.get(Calendar.HOUR_OF_DAY)

        grouped.keys.sorted().forEach { dateISO ->
            val hourMap = grouped[dateISO] ?: return@forEach
            hourMap.keys.sorted().forEach { hour ->
                // Guard: don't count violations for the current in-progress hour block
                if (dateISO == todayISO && hour == currentHour) return@forEach

                val entriesInHour = hourMap[hour] ?: return@forEach
                val loggedPoints = entriesInHour.filter { it.point != null }.mapNotNull { it.point }
                val missingPoints = activePoints.filter { !loggedPoints.contains(it) }

                if (missingPoints.isNotEmpty()) {
                    violations.add(
                        ViolationItem(
                            date = dateISO,
                            hour = hour,
                            type = "Missing points",
                            detail = "Missing: ${missingPoints.joinToString(", ")}"
                        )
                    )
                }
            }
        }

        // 2. Compute Gaps > 65m
        // Build chronological list of active hour structures where at least one point is tagged
        val hoursList = mutableListOf<HourBlockMeta>()
        grouped.keys.sorted().forEach { dateISO ->
            val hourMap = grouped[dateISO] ?: return@forEach
            hourMap.keys.sorted().forEach { hour ->
                val entriesInHour = hourMap[hour] ?: return@forEach
                val pointEntries = entriesInHour.filter { it.point != null }.sortedBy { it.timestamp }
                if (pointEntries.isNotEmpty()) {
                    hoursList.add(
                        HourBlockMeta(
                            dateISO = dateISO,
                            hour = hour,
                            firstTimestamp = pointEntries.first().timestamp,
                            lastTimestamp = pointEntries.last().timestamp
                        )
                    )
                }
            }
        }

        for (i in 1 until hoursList.size) {
            val prev = hoursList[i - 1]
            val curr = hoursList[i]
            val gapMinutes = (curr.firstTimestamp - prev.lastTimestamp) / (60 * 1000)
            if (gapMinutes > 65) {
                val prevTimeStr = SimpleDateFormat("HH:mm", Locale.US).format(Date(prev.lastTimestamp))
                violations.add(
                    ViolationItem(
                        date = curr.dateISO,
                        hour = curr.hour,
                        type = "Gap >65min",
                        detail = "$gapMinutes min gap from $prevTimeStr"
                    )
                )
            }
        }

        // 3. Coverage stats
        var totalMissedPoints = 0
        val hoursWorkedByDate = mutableMapOf<String, Int>()
        var globalMaxDayActiveHours = 0
        var globalMaxNightActiveHours = 0

        grouped.forEach { (dateKey, hourMap) ->
            var activeHours = 0
            val dayActiveHours = mutableSetOf<Int>()
            val nightActiveHours = mutableSetOf<Int>()

            hourMap.forEach { (hour, entries) ->
                val logged = entries.filter { it.point != null }.mapNotNull { it.point }
                val missing = activePoints.filter { !logged.contains(it) }
                totalMissedPoints += missing.size

                if (logged.isNotEmpty()) {
                    activeHours++
                    if (hour in 6..17) {
                        dayActiveHours.add(hour)
                    } else {
                        nightActiveHours.add(hour)
                    }
                }
            }
            hoursWorkedByDate[dateKey] = activeHours
            globalMaxDayActiveHours = maxOf(globalMaxDayActiveHours, dayActiveHours.size)
            globalMaxNightActiveHours = maxOf(globalMaxNightActiveHours, nightActiveHours.size)
        }

        // Weekly and Monthly aggregations
        val weekHours = mutableMapOf<String, Int>()
        val monthHours = mutableMapOf<String, Int>()

        hoursWorkedByDate.forEach { (dateKey, hours) ->
            try {
                val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey) ?: return@forEach
                val cal = Calendar.getInstance().apply { time = d }
                val wKey = getWeekKey(cal)
                val mKey = dateKey.substring(0, 7) // yyyy-MM
                weekHours[wKey] = (weekHours[wKey] ?: 0) + hours
                monthHours[mKey] = (monthHours[mKey] ?: 0) + hours
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val lastWeekKey = weekHours.keys.sorted().lastOrNull() ?: ""
        val lastMonthKey = monthHours.keys.sorted().lastOrNull() ?: ""

        PatrolStats(
            totalViolations = violations.size,
            lastViolations = violations.takeLast(10).reversed(),
            totalMissedPoints = totalMissedPoints,
            hoursWorkedToday = hoursWorkedByDate[todayISO] ?: 0,
            hoursWorkedThisWeek = weekHours[lastWeekKey] ?: 0,
            hoursWorkedThisMonth = monthHours[lastMonthKey] ?: 0,
            lastDayShiftHours = globalMaxDayActiveHours,
            lastNightShiftHours = globalMaxNightActiveHours
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PatrolStats(0, emptyList(), 0, 0, 0, 0, 0, 0)
    )

    private fun getWeekKey(cal: Calendar): String {
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        val year = cal.get(Calendar.YEAR)
        return "$year-W${String.format(Locale.US, "%02d", week)}"
    }

    data class HourBlockMeta(
        val dateISO: String,
        val hour: Int,
        val firstTimestamp: Long,
        val lastTimestamp: Long
    )

    // Monthly Report Computation
    // Monthly Report Computation
    fun calculateMonthlyReport() {
        val now = Calendar.getInstance()
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(now.time)
        val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.US).format(now.time)

        viewModelScope.launch {
            val allList = repository.getAllEntries()
            val monthEntries = allList.filter {
                it.dateISO.startsWith(monthKey)
            }

            if (monthEntries.isEmpty()) {
                monthlyReportData.value = MonthlyReport(
                    month = monthLabel,
                    totalEntries = 0,
                    totalHours = 0,
                    totalMisses = 0,
                    violations = 0,
                    bestDay = "--",
                    bestDayHours = 0,
                    worstDay = "--",
                    worstDayHours = 0
                )
                return@launch
            }

            val totalHoursSet = mutableSetOf<String>()
            var totalMisses = 0
            var violations = 0
            val pointLogs = monthEntries.filter { it.point != null }.sortedBy { it.timestamp }

            for (i in 1 until pointLogs.size) {
                val gap = (pointLogs[i].timestamp - pointLogs[i - 1].timestamp) / (60 * 1000)
                if (gap > 65) violations++
            }

            val grouped = mutableMapOf<String, MutableMap<Int, MutableList<PatrolEntry>>>()
            monthEntries.forEach { entry ->
                val d = Date(entry.timestamp)
                val dateKey = entry.dateISO
                val hour = Calendar.getInstance().apply { time = d }.get(Calendar.HOUR_OF_DAY)

                grouped.getOrPut(dateKey) { mutableMapOf() }
                    .getOrPut(hour) { mutableListOf() }
                    .add(entry)
            }

            grouped.forEach { (dateKey, hourMap) ->
                hourMap.forEach { (hour, entries) ->
                    val logged = entries.filter { it.point != null }.mapNotNull { it.point }
                    val missing = points.value.filter { !logged.contains(it) }
                    totalMisses += missing.size
                    if (logged.isNotEmpty()) {
                        totalHoursSet.add("$dateKey-$hour")
                    }
                }
            }

            val daysSortedByHours = grouped.keys.sortedBy { date ->
                grouped[date]?.values?.count { hList -> hList.any { it.point != null } } ?: 0
            }

            val bestDay = daysSortedByHours.lastOrNull() ?: "--"
            val bestDayHours = grouped[bestDay]?.values?.count { hList -> hList.any { it.point != null } } ?: 0

            val worstDay = daysSortedByHours.firstOrNull() ?: "--"
            val worstDayHours = grouped[worstDay]?.values?.count { hList -> hList.any { it.point != null } } ?: 0

            monthlyReportData.value = MonthlyReport(
                month = monthLabel,
                totalEntries = monthEntries.size,
                totalHours = totalHoursSet.size,
                totalMisses = totalMisses,
                violations = violations,
                bestDay = bestDay,
                bestDayHours = bestDayHours,
                worstDay = worstDay,
                worstDayHours = worstDayHours
            )
        }
    }

    fun closeMonthlyReport() {
        monthlyReportData.value = null
    }

    // CSV and Backup Handling
    suspend fun getExportString(format: String): String {
        val list = repository.getAllEntries()
        return when (format) {
            "csv" -> {
                val sb = java.lang.StringBuilder()
                sb.append("Date,Time,Point,Note,Timestamp,OutOfWindow\n")
                list.forEach {
                    val escNote = (it.note ?: "").replace("\"", "\"\"")
                    sb.append("\"${it.date}\",\"${it.time}\",\"${it.point ?: ""}\",\"$escNote\",\"${it.timestamp}\",\"${it.outOfWindow}\"\n")
                }
                sb.toString()
            }
            "json" -> {
                val backupList = list.map {
                    BackupEntry(
                        date = it.date,
                        dateISO = it.dateISO,
                        time = it.time,
                        point = it.point,
                        note = it.note,
                        timestamp = it.timestamp,
                        outOfWindow = it.outOfWindow
                    )
                }
                val payload = PatrolBackup(
                    version = "5.5",
                    exported = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()),
                    points = points.value,
                    patrols = backupList,
                    offDuty = offDuty.value,
                    emergencyNumber = emergencyNumber.value,
                    dutyStartTime = dutyStartTime.value
                )
                backupAdapter.toJson(payload)
            }
            else -> ""
        }
    }

    fun importBackup(jsonString: String, onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // Check if JSON array or PatrolBackup object
                val payload = if (jsonString.trim().startsWith("[")) {
                    // Raw fallback of list
                    val adapterList = moshi.adapter<List<BackupEntry>>(
                        com.squareup.moshi.Types.newParameterizedType(List::class.java, BackupEntry::class.java)
                    )
                    val patrolsParsed = adapterList.fromJson(jsonString) ?: emptyList()
                    PatrolBackup(
                        version = "5.5",
                        exported = "",
                        points = points.value,
                        patrols = patrolsParsed,
                        offDuty = offDuty.value,
                        emergencyNumber = emergencyNumber.value,
                        dutyStartTime = dutyStartTime.value
                    )
                } else {
                    backupAdapter.fromJson(jsonString)
                }

                if (payload == null) {
                    onError("Failed to parse file payload.")
                    return@launch
                }

                val converted = payload.patrols.map {
                    PatrolEntry(
                        date = it.date,
                        dateISO = it.dateISO,
                        time = it.time,
                        point = it.point,
                        note = it.note,
                        timestamp = it.timestamp,
                        outOfWindow = it.outOfWindow
                    )
                }

                if (converted.isNotEmpty()) {
                    setPoints(payload.points)
                    saveEmergencyNumber(payload.emergencyNumber)
                    offDuty.value = payload.offDuty
                    repository.preferences.offDuty = payload.offDuty
                    dutyStartTime.value = payload.dutyStartTime
                    repository.preferences.dutyStartTime = payload.dutyStartTime

                    repository.insertEntries(converted)
                    onSuccess(converted.size)
                } else {
                    onError("No entries found in backup template.")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Invalid file structure.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        closeAlarm()
        timerJob?.cancel()
        deadlineCheckJob?.cancel()
    }
}
