package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PatrolPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("patrol_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_OFF_DUTY = "off_duty"
        private const val KEY_EMERGENCY_NUMBER = "emergency_number"
        private const val KEY_PATROL_POINTS = "patrol_points"
        private const val KEY_DUTY_START_TIME = "duty_start_time"
    }

    var offDuty: Boolean
        get() = prefs.getBoolean(KEY_OFF_DUTY, true)
        set(value) = prefs.edit().putBoolean(KEY_OFF_DUTY, value).apply()

    var emergencyNumber: String
        get() = prefs.getString(KEY_EMERGENCY_NUMBER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EMERGENCY_NUMBER, value).apply()

    var patrolPoints: List<Int>
        get() {
            val raw = prefs.getString(KEY_PATROL_POINTS, "1,2,3,4,5") ?: "1,2,3,4,5"
            if (raw.isBlank()) return emptyList()
            return raw.split(",").mapNotNull { it.toIntOrNull() }
        }
        set(value) {
            val raw = value.joinToString(",")
            prefs.edit().putString(KEY_PATROL_POINTS, raw).apply()
        }

    var customPointNames: Map<Int, String>
        get() {
            val raw = prefs.getString("custom_point_names", "") ?: ""
            if (raw.isBlank()) return emptyMap()
            return raw.split(",").mapNotNull {
                val parts = it.split(":")
                if (parts.size == 2) {
                    val key = parts[0].toIntOrNull()
                    if (key != null) {
                        // Decode colon escape if any
                        val decodedValue = parts[1].replace("%3A", ":").replace("%2C", ",")
                        key to decodedValue
                    } else null
                } else null
            }.toMap()
        }
        set(value) {
            val raw = value.entries.joinToString(",") { 
                // Escape key-value separators
                val escapedValue = it.value.replace(",", "%2C").replace(":", "%3A")
                "${it.key}:$escapedValue"
            }
            prefs.edit().putString("custom_point_names", raw).apply()
        }

    var dutyStartTime: Long
        get() = prefs.getLong(KEY_DUTY_START_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_DUTY_START_TIME, value).apply()
}
