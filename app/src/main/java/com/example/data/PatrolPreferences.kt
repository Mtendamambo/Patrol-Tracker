package com.example.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class PatrolPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("patrol_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PATROL_INTERVAL = "patrol_interval"
        private const val KEY_START_HOUR = "start_hour"
        private const val KEY_END_HOUR = "end_hour"
        private const val KEY_EMERGENCY_NUMBER = "emergency_number"
        private const val KEY_ON_DUTY = "on_duty"
        private const val KEY_CUSTOM_POINTS = "custom_check_points"
        private const val KEY_ALARM_VOLUME = "alarm_volume"
    }

    var patrolInterval: Int
        get() = prefs.getInt(KEY_PATROL_INTERVAL, 15)
        set(value) = prefs.edit().putInt(KEY_PATROL_INTERVAL, value).apply()

    var startHour: Int
        get() = prefs.getInt(KEY_START_HOUR, 20)
        set(value) = prefs.edit().putInt(KEY_START_HOUR, value).apply()

    var endHour: Int
        get() = prefs.getInt(KEY_END_HOUR, 6)
        set(value) = prefs.edit().putInt(KEY_END_HOUR, value).apply()

    var emergencyNumber: String
        get() = prefs.getString(KEY_EMERGENCY_NUMBER, "0773554975") ?: "0773554975"
        set(value) = prefs.edit().putString(KEY_EMERGENCY_NUMBER, value).apply()

    var isOnDuty: Boolean
        get() = prefs.getBoolean(KEY_ON_DUTY, false)
        set(value) = prefs.edit().putBoolean(KEY_ON_DUTY, value).apply()

    var alarmVolume: Int
        get() = prefs.getInt(KEY_ALARM_VOLUME, 60)
        set(value) = prefs.edit().putInt(KEY_ALARM_VOLUME, value).apply()

    fun getCustomCheckpoints(): List<String> {
        val jsonStr = prefs.getString(KEY_CUSTOM_POINTS, null)
        if (jsonStr != null) {
            try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                return list
            } catch (e: Exception) {
                // fallback to default
            }
        }
        return listOf("Gates A", "Gates B", "Main Office", "Warehouse")
    }

    fun saveCustomCheckpoints(points: List<String>) {
        val array = JSONArray()
        points.forEach { array.put(it) }
        prefs.edit().putString(KEY_CUSTOM_POINTS, array.toString()).apply()
    }
}
