package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek

data class RosterCell(
    val day: Int,
    val code: String?,
    val hours: String?
)

data class RosterEmployee(
    val name: String,
    val totalHours: String,
    val shifts: Map<Int, RosterCell>
)

object RosterData {
    fun getCurrentMonthIndex(): Int {
        val today = java.time.LocalDate.now()
        val year = today.year
        val month = today.monthValue
        return if (year == 2025 && month == 12) {
            0
        } else if (year == 2026) {
            month
        } else if (year > 2026) {
            12
        } else {
            1
        }
    }

    var activeRosterMonth by mutableStateOf(getCurrentMonthIndex())
    
    // Application Context supplied dynamically
    var appContext: android.content.Context? = null

    // Reactive map of published month index to boolean
    val publishedMonthsMap = mutableStateMapOf<Int, Boolean>()

    var isJanuaryPublished: Boolean
        get() = publishedMonthsMap[1] == true
        set(value) {
            publishedMonthsMap[1] = value
        }

    fun getYearMonthForIndex(index: Int): java.time.YearMonth {
        if (index == 0) {
            return java.time.YearMonth.of(2025, 12)
        } else {
            return java.time.YearMonth.of(2026, index)
        }
    }

    fun isMonthAccessibleForUser(mIndex: Int, canEdit: Boolean): Boolean {
        if (mIndex == 0) return true // December 2025 is always public
        if (canEdit) return mIndex in 0..12
        return publishedMonthsMap[mIndex] == true
    }

    fun applyKolobehForMonth(m: Int) {
        if (m <= 0) return
        initMonths()
        val context = appContext
        if (context != null) {
            loadMonthlyRoster(context, m - 1)
        }
        val prevTop = monthlyTopEmployees[m - 1] ?: (if (m == 1) decemberTopEmployees else emptyList())
        val prevBottom = monthlyBottomEmployees[m - 1] ?: (if (m == 1) decemberBottomEmployees else emptyList())
        
        val allowedCodes = setOf("SR", "SN", "R", "N", "PR", "PN", "P", "V")
        val prevYm = getYearMonthForIndex(m - 1)
        val prevLength = prevYm.lengthOfMonth()
        
        val targetYm = getYearMonthForIndex(m)
        val targetLength = targetYm.lengthOfMonth()

        val newTop = prevTop.map { emp ->
            val emptyShifts = mutableMapOf<Int, RosterCell>()
            var weekendShiftsCount = 0
            val prevShifts = emp.shifts
            for (day in 1..prevLength) {
                val isWkPrev = isWeekend(day, monthIndex = m - 1)
                if (isWkPrev && prevShifts[day]?.code != null) {
                    weekendShiftsCount++
                }
            }
            
            if (weekendShiftsCount <= 1) {
                // Weekday employee: R or allowed code on weekdays, empty weekends
                val nonNilShifts = prevShifts.values.filter { it.code in allowedCodes }
                val commonCode = nonNilShifts.groupBy { it.code }.maxByOrNull { it.value.size }?.key ?: "R"
                val commonHrs = if (commonCode == "R") "7,5" else (nonNilShifts.find { it.code == commonCode }?.hours ?: "11,5")
                
                for (day in 1..targetLength) {
                    val isWkTarget = isWeekend(day, monthIndex = m)
                    if (isWkTarget) {
                        emptyShifts[day] = RosterCell(day, null, null)
                    } else {
                        emptyShifts[day] = RosterCell(day, commonCode, commonHrs)
                    }
                }
            } else {
                // Shift employee: project 4-day cycle from end of previous month
                val isSRFamily = prevShifts.values.any { it.code == "SR" || it.code == "SN" }
                for (day in 1..targetLength) {
                    val prevDaySource = (prevLength - 3) + (day - 1) % 4
                    var targetCode: String? = null
                    var targetHours: String? = null
                    var checkDay = prevDaySource
                    while (checkDay >= 1) {
                        val cell = prevShifts[checkDay]
                        val code = cell?.code
                        if (code == null || code == "NONE" || code == "Voľno") {
                            targetCode = null
                            targetHours = null
                            break
                        } else if (code in allowedCodes) {
                            targetCode = code
                            targetHours = cell.hours
                            break
                        } else {
                            checkDay -= 4
                        }
                    }
                    val finalCode = when {
                        targetCode == null -> null
                        targetCode in setOf("SR", "PR", "R") -> {
                            if (day <= targetLength / 2) {
                                if (isSRFamily) "SR" else "R"
                            } else {
                                "PR"
                            }
                        }
                        targetCode in setOf("SN", "PN", "N") -> {
                            if (day <= targetLength / 2) {
                                if (isSRFamily) "SN" else "N"
                            } else {
                                "PN"
                            }
                        }
                        else -> targetCode
                    }
                    emptyShifts[day] = RosterCell(day, finalCode, targetHours)
                }
            }
            val computedTotal = recalculateHours(emptyShifts)
            RosterEmployee(emp.name, computedTotal, emptyShifts)
        }

        val newBottom = prevBottom.map { emp ->
            val emptyShifts = mutableMapOf<Int, RosterCell>()
            var weekendShiftsCount = 0
            val prevShifts = emp.shifts
            for (day in 1..prevLength) {
                val isWkPrev = isWeekend(day, monthIndex = m - 1)
                if (isWkPrev && prevShifts[day]?.code != null) {
                    weekendShiftsCount++
                }
            }
            
            if (weekendShiftsCount <= 1) {
                val nonNilShifts = prevShifts.values.filter { it.code in allowedCodes }
                val commonCode = nonNilShifts.groupBy { it.code }.maxByOrNull { it.value.size }?.key ?: "R"
                val commonHrs = if (commonCode == "R") "7,5" else (nonNilShifts.find { it.code == commonCode }?.hours ?: "11,5")
                
                for (day in 1..targetLength) {
                    val isWkTarget = isWeekend(day, monthIndex = m)
                    if (isWkTarget) {
                        emptyShifts[day] = RosterCell(day, null, null)
                    } else {
                        emptyShifts[day] = RosterCell(day, commonCode, commonHrs)
                    }
                }
            } else {
                val isSRFamily = prevShifts.values.any { it.code == "SR" || it.code == "SN" }
                for (day in 1..targetLength) {
                    val prevDaySource = (prevLength - 3) + (day - 1) % 4
                    var targetCode: String? = null
                    var targetHours: String? = null
                    var checkDay = prevDaySource
                    while (checkDay >= 1) {
                        val cell = prevShifts[checkDay]
                        val code = cell?.code
                        if (code == null || code == "NONE" || code == "Voľno") {
                            targetCode = null
                            targetHours = null
                            break
                        } else if (code in allowedCodes) {
                            targetCode = code
                            targetHours = cell.hours
                            break
                        } else {
                            checkDay -= 4
                        }
                    }
                    val finalCode = when {
                        targetCode == null -> null
                        targetCode in setOf("SR", "PR", "R") -> {
                            if (day <= targetLength / 2) {
                                if (isSRFamily) "SR" else "R"
                            } else {
                                "PR"
                            }
                        }
                        targetCode in setOf("SN", "PN", "N") -> {
                            if (day <= targetLength / 2) {
                                if (isSRFamily) "SN" else "N"
                            } else {
                                "PN"
                            }
                        }
                        else -> targetCode
                    }
                    emptyShifts[day] = RosterCell(day, finalCode, targetHours)
                }
            }
            val computedTotal = recalculateHours(emptyShifts)
            RosterEmployee(emp.name, computedTotal, emptyShifts)
        }

        monthlyTopEmployees[m] = newTop
        monthlyBottomEmployees[m] = newBottom
        
        if (m == activeRosterMonth) {
            topEmployees = newTop
            bottomEmployees = newBottom
        }
        if (context != null) {
            saveMonthlyRoster(context, m)
        }

        // Auto-synchronize the updated roster shift cells with the shift tracker (Šichér) database on kolobeh run
        val allEmployees = newTop + newBottom
        val oldActive = activeRosterMonth
        activeRosterMonth = m
        for (emp in allEmployees) {
            emp.shifts.forEach { (day, cell) ->
                onCellUpdatedExternal?.invoke(emp.name, day, cell.code, cell.hours)
            }
        }
        activeRosterMonth = oldActive
    }

    // Synchronize roster cell updates with the local shifts database (Šichter)
    var onCellUpdatedExternal: ((String, Int, String?, String?) -> Unit)? = null

    // Proactive full sync of roster shifts to shift tracker database
    fun syncRosterToSichereForUser(employeeName: String, monthIndex: Int) {
        val ym = getYearMonthForIndex(monthIndex)
        val numDays = ym.lengthOfMonth()
        
        val topList = monthlyTopEmployees[monthIndex] ?: emptyList()
        val bottomList = monthlyBottomEmployees[monthIndex] ?: emptyList()
        val combined = topList + bottomList
        
        val emp = combined.find { it.name.trim().equals(employeeName.trim(), ignoreCase = true) }
        if (emp != null) {
            val oldActive = activeRosterMonth
            activeRosterMonth = monthIndex
            for (day in 1..numDays) {
                val cell = emp.shifts[day]
                onCellUpdatedExternal?.invoke(emp.name, day, cell?.code, cell?.hours)
            }
            activeRosterMonth = oldActive
        }
    }

    // Reactive in-app notifications list
    var inAppNotifications by mutableStateOf<List<Triple<String, String, Long>>>(emptyList())

    fun loadInAppNotifications(context: android.content.Context) {
        val prefs = context.getSharedPreferences("shift_prefs", android.content.Context.MODE_PRIVATE)
        val currentSet = prefs.getStringSet("inapp_notifications", emptySet()) ?: emptySet()
        inAppNotifications = currentSet.mapNotNull { item ->
            val parts = item.split("|")
            if (parts.size >= 4) {
                val title = parts[1]
                val text = parts[2]
                val ts = parts[3].toLongOrNull() ?: 0L
                Triple(title, text, ts)
            } else {
                null
            }
        }.sortedByDescending { it.third }
    }

    fun triggerRosterNotification(context: android.content.Context, title: String, message: String) {
        val prefs = context.getSharedPreferences("shift_prefs", android.content.Context.MODE_PRIVATE)
        val isPushEnabled = prefs.getBoolean("roster_notifications_enabled", true)
        
        if (isPushEnabled) {
            // Send system push notification
            com.example.ReminderReceiver.showNotification(context, title, message)
        } else {
            // Logged-in user has notifications turned off, so store it as in-app notification
            val currentSet = prefs.getStringSet("inapp_notifications", emptySet())?.toMutableSet() ?: mutableSetOf()
            val id = java.util.UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val cleanTitle = title.replace("|", " ")
            val cleanMessage = message.replace("|", " ")
            currentSet.add("$id|$cleanTitle|$cleanMessage|$timestamp")
            prefs.edit().putStringSet("inapp_notifications", currentSet).apply()
            
            // Reload into reactive state immediately
            loadInAppNotifications(context)
        }
    }

    fun clearInAppNotifications(context: android.content.Context) {
        val prefs = context.getSharedPreferences("shift_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putStringSet("inapp_notifications", emptySet()).apply()
        inAppNotifications = emptyList()
    }

    // JSON Serialization utilities for RosterEmployee persistence
    fun serializeEmployees(employees: List<RosterEmployee>): String {
        val array = JSONArray()
        for (emp in employees) {
            val empObj = JSONObject()
            empObj.put("name", emp.name)
            empObj.put("totalHours", emp.totalHours)
            
            val shiftsArray = JSONArray()
            for ((day, cell) in emp.shifts) {
                val cellObj = JSONObject()
                cellObj.put("day", cell.day)
                if (cell.code != null) cellObj.put("code", cell.code)
                if (cell.hours != null) cellObj.put("hours", cell.hours)
                shiftsArray.put(cellObj)
            }
            empObj.put("shifts", shiftsArray)
            array.put(empObj)
        }
        return array.toString()
    }

    fun deserializeEmployees(jsonStr: String): List<RosterEmployee> {
        val list = mutableListOf<RosterEmployee>()
        if (jsonStr.isBlank()) return list
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val empObj = array.getJSONObject(i)
                val name = empObj.getString("name")
                val totalHours = empObj.getString("totalHours")
                
                val shiftsMap = mutableMapOf<Int, RosterCell>()
                val shiftsArray = empObj.getJSONArray("shifts")
                for (j in 0 until shiftsArray.length()) {
                    val cellObj = shiftsArray.getJSONObject(j)
                    val day = cellObj.getInt("day")
                    val code = if (cellObj.has("code")) cellObj.getString("code") else null
                    val hours = if (cellObj.has("hours")) cellObj.getString("hours") else null
                    shiftsMap[day] = RosterCell(day, code, hours)
                }
                list.add(RosterEmployee(name, totalHours, shiftsMap))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveMonthlyRoster(context: android.content.Context, m: Int) {
        val prefs = context.getSharedPreferences("shift_prefs", android.content.Context.MODE_PRIVATE)
        val topList = if (m == activeRosterMonth) topEmployees else (monthlyTopEmployees[m] ?: emptyList())
        val bottomList = if (m == activeRosterMonth) bottomEmployees else (monthlyBottomEmployees[m] ?: emptyList())
        
        if (topList.isNotEmpty()) {
            val serializedTop = serializeEmployees(topList)
            prefs.edit().putString("roster_month_${m}_top_employees", serializedTop).apply()
        }
        if (bottomList.isNotEmpty()) {
            val serializedBottom = serializeEmployees(bottomList)
            prefs.edit().putString("roster_month_${m}_bottom_employees", serializedBottom).apply()
        }
    }

    fun loadMonthlyRoster(context: android.content.Context, m: Int) {
        val prefs = context.getSharedPreferences("shift_prefs", android.content.Context.MODE_PRIVATE)
        
        val savedTop = prefs.getString("roster_month_${m}_top_employees", null)
        val savedBottom = prefs.getString("roster_month_${m}_bottom_employees", null)
        
        val topList = if (savedTop != null) {
            deserializeEmployees(savedTop)
        } else {
            if (m == 0) {
                decemberTopEmployees
            } else if (m == 6) {
                getJuneTopEmployees()
            } else {
                val previousTop = monthlyTopEmployees[m - 1] ?: (if (m == 1) decemberTopEmployees else emptyList())
                val ym = getYearMonthForIndex(m)
                previousTop.map { emp ->
                    val emptyShifts = mutableMapOf<Int, RosterCell>()
                    for (day in 1..ym.lengthOfMonth()) {
                        emptyShifts[day] = RosterCell(day, null, null)
                    }
                    RosterEmployee(emp.name, "0", emptyShifts)
                }
            }
        }
        
        val bottomList = if (savedBottom != null) {
            deserializeEmployees(savedBottom)
        } else {
            if (m == 0) {
                decemberBottomEmployees
            } else if (m == 6) {
                getJuneBottomEmployees()
            } else {
                val previousBottom = monthlyBottomEmployees[m - 1] ?: (if (m == 1) decemberBottomEmployees else emptyList())
                val ym = getYearMonthForIndex(m)
                previousBottom.map { emp ->
                    val emptyShifts = mutableMapOf<Int, RosterCell>()
                    for (day in 1..ym.lengthOfMonth()) {
                        emptyShifts[day] = RosterCell(day, null, null)
                    }
                    RosterEmployee(emp.name, "0", emptyShifts)
                }
            }
        }
        
        monthlyTopEmployees[m] = topList
        monthlyBottomEmployees[m] = bottomList
        
        if (m == activeRosterMonth) {
            topEmployees = topList
            bottomEmployees = bottomList
        }
    }

    fun loadPublishedStates(context: android.content.Context) {
        val prefs = context.getSharedPreferences("shift_prefs", android.content.Context.MODE_PRIVATE)
        publishedMonthsMap[0] = true // Dec 2025 is always published
        for (m in 1..12) {
            val mKey = "roster_month_${m}_published"
            val defaultKey = if (m == 1) "roster_january_published" else mKey
            publishedMonthsMap[m] = prefs.getBoolean(mKey, prefs.getBoolean(defaultKey, false))
        }
    }

    fun publishMonth(context: android.content.Context, m: Int) {
        val prefs = context.getSharedPreferences("shift_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("roster_month_${m}_published", true).apply()
        if (m == 1) {
            prefs.edit().putBoolean("roster_january_published", true).apply()
        }
        publishedMonthsMap[m] = true
    }

    fun resetToDefaultPattern(context: android.content.Context, m: Int) {
        val prefs = context.getSharedPreferences("shift_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .remove("roster_month_${m}_top_employees")
            .remove("roster_month_${m}_bottom_employees")
            .apply()
        
        // Remove from memory maps to force reloading
        monthlyTopEmployees.remove(m)
        monthlyBottomEmployees.remove(m)
        
        // Load default again
        loadMonthlyRoster(context, m)
        
        // If m is current active month, update the state
        if (m == activeRosterMonth) {
            topEmployees = monthlyTopEmployees[m] ?: emptyList()
            bottomEmployees = monthlyBottomEmployees[m] ?: emptyList()
        }
        
        // Sync to Firestore in background
        if (FirebaseSync.isConnected) {
            FirebaseSync.uploadCurrentRosterToFirestore(context)
        }
    }

    fun saveCurrentState() {
        val context = appContext ?: return
        monthlyTopEmployees[activeRosterMonth] = topEmployees
        monthlyBottomEmployees[activeRosterMonth] = bottomEmployees
        saveMonthlyRoster(context, activeRosterMonth)
        
        // Dynamic Sync to Firestore in background
        if (FirebaseSync.isConnected) {
            FirebaseSync.uploadCurrentRosterToFirestore(context)
        }
    }

    // Memory storage for top and bottom employees of all 13 months
    val monthlyTopEmployees = mutableMapOf<Int, List<RosterEmployee>>()
    val monthlyBottomEmployees = mutableMapOf<Int, List<RosterEmployee>>()

    var decemberTopEmployees = listOf<RosterEmployee>()
    var decemberBottomEmployees = listOf<RosterEmployee>()

    var nextMonthTopEmployees = listOf<RosterEmployee>()
    var nextMonthBottomEmployees = listOf<RosterEmployee>()

    fun initMonths() {
        if (decemberTopEmployees.isEmpty()) {
            decemberTopEmployees = topEmployees
        }
        if (decemberBottomEmployees.isEmpty()) {
            decemberBottomEmployees = bottomEmployees
        }
        val context = appContext
        if (context != null) {
            loadPublishedStates(context)
            for (m in 0..12) {
                if (!monthlyTopEmployees.containsKey(m)) {
                    loadMonthlyRoster(context, m)
                }
            }
        }
    }

    fun initializeJanuaryIfNeeded() {
        initMonths()
        if (nextMonthTopEmployees.isEmpty()) {
            nextMonthTopEmployees = decemberTopEmployees.map { emp ->
                val emptyShifts = mutableMapOf<Int, RosterCell>()
                for (day in 1..31) {
                    emptyShifts[day] = RosterCell(day, null, null)
                }
                RosterEmployee(emp.name, "0", emptyShifts)
            }
        }
        if (nextMonthBottomEmployees.isEmpty()) {
            nextMonthBottomEmployees = decemberBottomEmployees.map { emp ->
                val emptyShifts = mutableMapOf<Int, RosterCell>()
                for (day in 1..31) {
                    emptyShifts[day] = RosterCell(day, null, null)
                }
                RosterEmployee(emp.name, "0", emptyShifts)
            }
        }
    }

    fun switchMonth(monthIndex: Int) {
        initMonths()
        if (activeRosterMonth == monthIndex) return
        
        // Save current month lists to their respective backups
        val context = appContext
        if (context != null) {
            saveMonthlyRoster(context, activeRosterMonth)
        }
        monthlyTopEmployees[activeRosterMonth] = topEmployees
        monthlyBottomEmployees[activeRosterMonth] = bottomEmployees
        if (activeRosterMonth == 0) {
            decemberTopEmployees = topEmployees
            decemberBottomEmployees = bottomEmployees
        } else if (activeRosterMonth == 1) {
            nextMonthTopEmployees = topEmployees
            nextMonthBottomEmployees = bottomEmployees
        }
        
        // Load target month's lists
        activeRosterMonth = monthIndex
        if (context != null) {
            if (!monthlyTopEmployees.containsKey(monthIndex)) {
                loadMonthlyRoster(context, monthIndex)
            }
        }
        val loadedTop = monthlyTopEmployees[monthIndex]
        val loadedBottom = monthlyBottomEmployees[monthIndex]
        if (loadedTop != null && loadedBottom != null) {
            topEmployees = loadedTop
            bottomEmployees = loadedBottom
        } else {
            // fallback if context or load is not ready
            if (monthIndex == 0) {
                topEmployees = decemberTopEmployees
                bottomEmployees = decemberBottomEmployees
            } else {
                initializeJanuaryIfNeeded()
                topEmployees = nextMonthTopEmployees
                bottomEmployees = nextMonthBottomEmployees
            }
        }

        // Live syncing setup
        if (context != null && FirebaseSync.isConnected) {
            FirebaseSync.startListeningCurrentMonth(context)
        }
    }

    fun applyKolobehForJanuary() {
        initMonths()
        // Ensure January backing lists are created
        initializeJanuaryIfNeeded()
        
        val decTop = if (activeRosterMonth == 0) topEmployees else decemberTopEmployees
        val decBottom = if (activeRosterMonth == 0) bottomEmployees else decemberBottomEmployees
        
        val allowedCodes = setOf("SR", "SN", "R", "N", "PR", "PN", "P", "V")

        val newTop = decTop.map { emp ->
            val emptyShifts = mutableMapOf<Int, RosterCell>()
            var weekendShiftsCount = 0
            val decShifts = emp.shifts
            for (day in 1..31) {
                val isWkDec = isWeekend(day, monthIndex = 0)
                if (isWkDec && decShifts[day]?.code != null) {
                    weekendShiftsCount++
                }
            }
            
            if (weekendShiftsCount <= 1) {
                // Weekday employee: R or allowed code on weekdays, empty weekends
                val nonNilShifts = decShifts.values.filter { it.code in allowedCodes }
                val commonCode = nonNilShifts.groupBy { it.code }.maxByOrNull { it.value.size }?.key ?: "R"
                val commonHrs = if (commonCode == "R") "7,5" else (nonNilShifts.find { it.code == commonCode }?.hours ?: "11,5")
                
                for (day in 1..31) {
                    val isWkJan = isWeekend(day, monthIndex = 1)
                    if (isWkJan) {
                        emptyShifts[day] = RosterCell(day, null, null)
                    } else {
                        emptyShifts[day] = RosterCell(day, commonCode, commonHrs)
                    }
                }
            } else {
                // Shift employee: project 4-day cycle from end of December, skipping holidays/KZ/CH
                val isSRFamily = decShifts.values.any { it.code == "SR" || it.code == "SN" }
                for (day in 1..31) {
                    val decDaySource = 28 + (day - 1) % 4
                    var targetCode: String? = null
                    var targetHours: String? = null
                    var checkDay = decDaySource
                    while (checkDay >= 1) {
                        val cell = decShifts[checkDay]
                        val code = cell?.code
                        if (code == null || code == "NONE" || code == "Voľno") {
                            targetCode = null
                            targetHours = null
                            break
                        } else if (code in allowedCodes) {
                            targetCode = code
                            targetHours = cell.hours
                            break
                        } else {
                            // Skip holidays/KZ/CH (e.g., D, KZ, KZS, KZV, KZVS, CH, PN, Par etc.) and trace back 4 days to find cycle source
                            checkDay -= 4
                        }
                    }
                    val finalCode = when {
                        targetCode == null -> null
                        targetCode in setOf("SR", "PR", "R") -> {
                            if (day <= 15) { // 31 days month, first half is <=15
                                if (isSRFamily) "SR" else "R"
                            } else {
                                "PR"
                            }
                        }
                        targetCode in setOf("SN", "PN", "N") -> {
                            if (day <= 15) {
                                if (isSRFamily) "SN" else "N"
                            } else {
                                "PN"
                            }
                        }
                        else -> targetCode
                    }
                    emptyShifts[day] = RosterCell(day, finalCode, targetHours)
                }
            }
            val computedTotal = recalculateHours(emptyShifts)
            RosterEmployee(emp.name, computedTotal, emptyShifts)
        }

        val newBottom = decBottom.map { emp ->
            val emptyShifts = mutableMapOf<Int, RosterCell>()
            var weekendShiftsCount = 0
            val decShifts = emp.shifts
            for (day in 1..31) {
                val isWkDec = isWeekend(day, monthIndex = 0)
                if (isWkDec && decShifts[day]?.code != null) {
                    weekendShiftsCount++
                }
            }
            
            if (weekendShiftsCount <= 1) {
                val nonNilShifts = decShifts.values.filter { it.code in allowedCodes }
                val commonCode = nonNilShifts.groupBy { it.code }.maxByOrNull { it.value.size }?.key ?: "R"
                val commonHrs = if (commonCode == "R") "7,5" else (nonNilShifts.find { it.code == commonCode }?.hours ?: "11,5")
                
                for (day in 1..31) {
                    val isWkJan = isWeekend(day, monthIndex = 1)
                    if (isWkJan) {
                        emptyShifts[day] = RosterCell(day, null, null)
                    } else {
                        emptyShifts[day] = RosterCell(day, commonCode, commonHrs)
                    }
                }
            } else {
                val isSRFamily = decShifts.values.any { it.code == "SR" || it.code == "SN" }
                for (day in 1..31) {
                    val decDaySource = 28 + (day - 1) % 4
                    var targetCode: String? = null
                    var targetHours: String? = null
                    var checkDay = decDaySource
                    while (checkDay >= 1) {
                        val cell = decShifts[checkDay]
                        val code = cell?.code
                        if (code == null || code == "NONE" || code == "Voľno") {
                            targetCode = null
                            targetHours = null
                            break
                        } else if (code in allowedCodes) {
                            targetCode = code
                            targetHours = cell.hours
                            break
                        } else {
                            // Skip holidays/KZ/CH and trace back
                            checkDay -= 4
                        }
                    }
                    val finalCode = when {
                        targetCode == null -> null
                        targetCode in setOf("SR", "PR", "R") -> {
                            if (day <= 15) {
                                if (isSRFamily) "SR" else "R"
                            } else {
                                "PR"
                            }
                        }
                        targetCode in setOf("SN", "PN", "N") -> {
                            if (day <= 15) {
                                if (isSRFamily) "SN" else "N"
                            } else {
                                "PN"
                            }
                        }
                        else -> targetCode
                    }
                    emptyShifts[day] = RosterCell(day, finalCode, targetHours)
                }
            }
            val computedTotal = recalculateHours(emptyShifts)
            RosterEmployee(emp.name, computedTotal, emptyShifts)
        }

        nextMonthTopEmployees = newTop
        nextMonthBottomEmployees = newBottom
        
        if (activeRosterMonth == 1) {
            topEmployees = newTop
            bottomEmployees = newBottom
        }
    }

    fun getDayOfWeek(day: Int, monthIndex: Int = activeRosterMonth): String {
        return try {
            val ym = getYearMonthForIndex(monthIndex)
            val date = java.time.LocalDate.of(ym.year, ym.monthValue, day)
            val dow = date.dayOfWeek
            when (dow) {
                java.time.DayOfWeek.MONDAY -> "Po"
                java.time.DayOfWeek.TUESDAY -> "Ut"
                java.time.DayOfWeek.WEDNESDAY -> "St"
                java.time.DayOfWeek.THURSDAY -> "Št"
                java.time.DayOfWeek.FRIDAY -> "Pi"
                java.time.DayOfWeek.SATURDAY -> "So"
                java.time.DayOfWeek.SUNDAY -> "Ne"
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun isWeekend(day: Int, monthIndex: Int = activeRosterMonth): Boolean {
        return try {
            val ym = getYearMonthForIndex(monthIndex)
            val date = java.time.LocalDate.of(ym.year, ym.monthValue, day)
            val dow = date.dayOfWeek
            dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY
        } catch (e: Exception) {
            false
        }
    }

    fun isHoliday(day: Int, monthIndex: Int = activeRosterMonth): Boolean {
        return try {
            val ym = getYearMonthForIndex(monthIndex)
            val m = ym.monthValue
            when (m) {
                1 -> day == 1 || day == 6
                5 -> day == 1 || day == 8
                7 -> day == 5
                8 -> day == 29
                9 -> day == 1 || day == 15
                11 -> day == 1 || day == 17
                12 -> day == 24 || day == 25 || day == 26
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    val slovakHolidays = setOf(24, 25, 26) // Dec 24, 25, 26

    // Transcribe employees and their shifts for Dec 2025 using a flat vararg structure to avoid any kotlin map compiler issue
    var topEmployees by mutableStateOf(listOf(
        createEmployee("Mgr. Adámik M.", "172,5",
            1, "D", "7,5", 2, "R", "7,5", 3, "R", "7,5", 4, "R", "7,5", 5, "R", "7,5",
            8, "R", "7,5", 9, "R", "7,5", 10, "R", "7,5", 11, "R", "7,5", 12, "R", "7,5",
            15, "R", "7,5", 16, "R", "7,5", 17, "R", "7,5", 18, "R", "7,5", 19, "R", "7,5",
            22, "R", "7,5", 23, "R", "7,5", 24, "R", "7,5", 25, "R", "7,5", 26, "R", "7,5",
            29, "R", "7,5", 30, "R", "7,5", 31, "R", "7,5"
        ),
        createEmployee("Bc. Kovančík", "172,5",
            1, "R", "7,5", 2, "R", "7,5", 3, "R", "7,5", 4, "R", "7,5", 5, "R", "7,5",
            8, "R", "7,5", 9, "R", "7,5", 10, "R", "7,5", 11, "R", "7,5", 12, "D", "7,5",
            15, "R", "7,5", 16, "D", "7,5", 17, "D", "7,5", 18, "D", "7,5", 19, "D", "7,5",
            22, "D", "7,5", 23, "D", "7,5", 24, "D", "7,5", 25, "D", "7,5", 26, "R", "7,5",
            29, "R", "7,5", 30, "R", "7,5", 31, "R", "7,5"
        ),
        createEmployee("Kováčová R.", "172,5",
            1, "R", "7,5", 2, "R", "7,5", 3, "R", "7,5", 4, "R", "7,5", 5, "R", "7,5",
            8, "R", "7,5", 9, "R", "7,5", 10, "R", "7,5", 11, "R", "7,5", 12, "R", "7,5",
            15, "R", "7,5", 16, "R", "7,5", 17, "R", "7,5", 18, "R", "7,5", 19, "R", "7,5",
            22, "R", "7,5", 23, "R", "7,5", 24, "R", "7,5", 25, "R", "7,5", 26, "R", "7,5",
            29, "R", "7,5", 30, "R", "7,5", 31, "R", "7,5"
        ),
        createEmployee("Bc. Adámik R.", "172,5",
            1, "CH", "7,5", 2, "CH", "7,5", 3, "CH", "7,5", 4, "CH", "7,5", 5, "CH", "7,5",
            8, "CH", "7,5", 9, "CH", "7,5", 10, "CH", "7,5", 11, "CH", "7,5", 12, "CH", "7,5",
            15, "CH", "7,5", 16, "CH", "7,5", 17, "CH", "7,5", 18, "CH", "7,5", 19, "CH", "7,5",
            22, "CH", "7,5", 23, "CH", "7,5", 24, "CH", "7,5", 25, "CH", "7,5", 26, "CH", "7,5",
            29, "CH", "7,5", 30, "CH", "7,5", 31, "CH", "7,5"
        ),
        createEmployee("Roštáš A.", "176,5",
            1, "R", "7,5", 2, "R", "7,5", 3, "R", "7,5", 4, "SR", "11,5", 5, "R", "7,5",
            8, "R", "7,5", 9, "R", "7,5", 10, "R", "7,5", 11, "R", "7,5", 12, "R", "7,5",
            15, "R", "7,5", 16, "R", "7,5", 17, "R", "7,5", 18, "R", "7,5", 19, "R", "7,5",
            22, "R", "7,5", 23, "D", "7,5", 24, "D", "7,5", 25, "D", "7,5", 26, "R", "7,5",
            29, "D", "7,5", 30, "D", "7,5", 31, "D", "7,5"
        ),
        createEmployee("Bc. Peťko M.", "177,0",
            1, "R", "7,5", 2, "R", "7,5", 3, "R", "7,5", 4, "R", "7,5", 5, "SN", "11,5",
            8, "SR", "11,5", 9, "SN", "11,5", 10, "R", "7,5", 11, "R", "7,5", 12, "R", "7,5",
            15, "R", "7,5", 16, "R", "7,5", 17, "R", "7,5", 18, "R", "7,5", 19, "R", "7,5",
            22, "R", "7,5", 23, "R", "7,5", 24, "R", "7,5", 25, "R", "7,5", 26, "R", "7,5",
            29, "R", "7,5", 30, "R", "7,5", 31, "R", "7,5"
        ),
        createEmployee("Bc. Šebelédy", "183,0",
            1, "R", "7,5", 2, "R", "7,5", 3, "R", "7,5", 4, "SN", "13,5",
            8, "R", "7,5", 9, "R", "7,5", 10, "R", "7,5", 11, "R", "7,5", 12, "SR", "11,5",
            15, "SR", "11,5", 16, "R", "7,5", 17, "R", "7,5", 18, "R", "7,5", 19, "R", "7,5",
            22, "R", "7,5", 23, "SR", "11,5", 24, "R", "7,5", 25, "R", "7,5", 26, "R", "7,5",
            29, "R", "7,5", 30, "R", "7,5", 31, "R", "7,5"
        ),
        createEmployee("Mgr. Krenčan", "180,5",
            1, "Par", "7,5", 2, "R", "7,5", 3, "R", "7,5", 4, "R", "7,5", 5, "R", "7,5",
            8, "R", "7,5", 9, "R", "7,5", 10, "SR", "11,5", 11, "R", "7,5", 12, "R", "7,5",
            15, "R", "7,5", 16, "R", "7,5", 17, "R", "7,5", 18, "R", "7,5", 19, "R", "7,5",
            22, "R", "7,5", 23, "R", "7,5", 24, "R", "7,5", 25, "R", "7,5", 26, "R", "7,5",
            29, "SR", "11,5", 30, "KZV", "7,5", 31, "KZV", "7,5"
        ),
        createEmployee("Bc. Töröková", "173,0",
            1, "CH", "7,5", 2, "CH", "7,5", 3, "CH", "7,5", 4, "CH", "7,5", 5, "CH", "7,5",
            8, "R", "7,5", 9, "R", "7,5", 10, "R", "7,5", 11, "SN", "11,5",
            15, "R", "7,5", 16, "R", "7,5", 17, "SR", "11,5", 18, "R", "7,5", 19, "R", "7,5",
            22, "D", "7,5", 23, "D", "7,5", 24, "D", "7,5", 25, "D", "7,5", 26, "D", "7,5",
            29, "R", "7,5", 30, "R", "7,5", 31, "R", "7,5"
        ),
        createEmployee("Bielik R.", "174,5",
            3, "SR", "11,5", 4, "D" , "11,5", 7, "SR", "11,5", 8, "SN", "11,5",
            11, "SR", "11,5", 12, "SN", "11,5", 15, "D" , "11,5", 16, "SN", "11,5",
            19, "SR", "11,5", 20, "SN", "11,5", 23, "D" , "11,5", 24, "SR", "11,5",
            27, "SR", "11,5", 28, "SN", "11,5", 31, "SR", "11,5"
        ),
        createEmployee("Fábry J.", "182,0",
            4, "D" , "11,5", 5, "PN" , "13,5", 8, "D", "11,5", 9, "D", "11,5", 10, "P", "7,5",
            12, "D" , "11,5", 13, "PR" , "5,0", 14, "PN", "11,5", 15, "N", "11,5",
            18, "PR" , "5,0", 19, "PN" , "11,5", 22, "R", "11,5", 23, "N", "11,5",
            24, "PN" , "11,5", 28, "N"  , "11,5", 31, "R", "11,5"
        ),
        createEmployee("Kazár D.", "174,5",
            2, "PR", "11,5", 3, "PN" , "13,5", 4, "CH", "11,5", 5, "CH", "11,5", 6, "CH", "11,5", 7, "CH", "11,5",
            8, "P" , "11,5", 9, "P"  , "11,5", 10, "P", "11,5", 11, "P", "11,5", 12, "CH", "11,5", 13, "CH", "11,5", 14, "CH", "11,5",
            17, "PR", "11,5", 18, "PN" , "11,5", 22, "R", "11,5", 23, "N", "11,5",
            26, "N" , "11,5", 27, "D"  , "11,5", 30, "D", "11,5"
        ),
        createEmployee("PhDr. Kabát", "174,5",
            2, "PR", "11,5", 3, "R"  , "11,5", 4, "P", "2,0", 7, "N", "11,5",
            9, "N" , "11,5", 10, "R"  , "11,5", 11, "PR", "11,5", 12, "D", "11,5",
            14, "KZS", "11,5", 15, "KZS" , "11,5", 16, "D", "11,5", 17, "D", "11,5",
            21, "D" , "11,5", 26, "PR" , "11,5", 27, "PN", "11,5"
        ),
        createEmployee("Polyák A.", "174,5",
            3, "R"  , "11,5", 4, "N"  , "13,5", 7, "PR", "11,5", 8, "PN", "11,5",
            11, "KZS", "11,5", 12, "KZS" , "11,5", 15, "PR", "11,5", 16, "PN", "11,5",
            19, "R"  , "11,5", 20, "N"  , "11,5", 23, "PR", "11,5", 24, "PN", "11,5",
            27, "R"  , "11,5", 28, "N"  , "11,5", 31, "PR", "11,5"
        ),
        createEmployee("Bc. Petráš A.", "174,5",
            3, "R"  , "11,5", 4, "N"  , "13,5", 7, "PR", "11,5", 8, "PN", "11,5",
            11, "KZS", "11,5", 12, "KZS" , "11,5", 15, "PR", "11,5", 16, "PN", "11,5",
            19, "R"  , "11,5", 20, "N"  , "11,5", 23, "PR", "11,5", 24, "PN", "11,5",
            27, "R"  , "11,5", 28, "N"  , "11,5", 31, "PR", "11,5"
        ),
        createEmployee("Bc. Kelo R.", "172,5",
            1, "SN", "11,5", 4, "D" , "11,5", 5, "D", "11,5", 7, "D", "11,5",
            8, "D" , "11,5", 11, "D" , "11,5", 12, "SN", "11,5", 15, "SR", "11,5",
            16, "SN", "11,5", 19, "SR", "11,5", 20, "SN", "11,5", 23, "SR", "11,5",
            24, "SN", "11,5", 27, "SR", "11,5", 28, "SN", "11,5"
        ),
        createEmployee("Andruška Z.", "172,5",
            1, "N" , "11,5", 3, "Par", "11,5", 4, "PN", "11,5", 7, "PR", "11,5",
            8, "P" , "7,5", 11, "R" , "11,5", 12, "N" , "11,5", 15, "PR", "11,5",
            16, "PN", "11,5", 19, "R" , "11,5", 20, "N" , "11,5", 23, "PR", "11,5",
            24, "PN", "11,5", 28, "R" , "11,5"
        ),
        createEmployee("Bc. Masaryk", "180,0",
            1, "PN", "11,5", 3, "PR", "11,5", 4, "N" , "11,5", 6, "Par", "11,5",
            7, "Par", "11,5", 11, "R" , "11,5", 12, "PN", "11,5", 15, "R" , "11,5",
            16, "N" , "11,5", 19, "PR", "11,5", 20, "PN", "11,5", 23, "PR", "11,5",
            24, "PN", "11,5", 27, "R" , "11,5", 28, "D" , "11,5"
        ),
        createEmployee("Šovčík M.", "172,5",
            1, "PN", "11,5", 3, "D" , "11,5", 4, "D" , "11,5", 7, "PN", "11,5",
            10, "PR", "11,5", 11, "PR", "11,5", 12, "PN", "11,5", 15, "R" , "11,5",
            16, "N" , "11,5", 19, "PR", "11,5", 20, "PN", "11,5", 23, "R" , "11,5",
            24, "N" , "11,5", 27, "R" , "11,5", 28, "N" , "11,5"
        )
    ))

    var bottomEmployees by mutableStateOf(listOf(
        createEmployee("Bc. Obert L.", "174,5",
            1, "SR", "11,5", 2, "SN", "11,5", 4, "P", "2,0", 5, "SR", "11,5", 6, "SN", "11,5",
            9, "SR", "11,5", 10, "SN", "11,5", 13, "SR", "11,5", 14, "SN", "11,5",
            17, "SN", "11,5", 20, "SR", "11,5", 21, "SN", "11,5", 24, "SR", "11,5",
            25, "SN", "11,5", 28, "D" , "11,5", 29, "SN", "11,5"
        ),
        createEmployee("Adámik P.", "182,0",
            1, "R" , "11,5", 3, "PR", "11,5", 4, "R" , "11,5", 5, "N" , "11,5",
            9, "P" , "11,5", 10, "PN", "11,5", 11, "PN", "11,5", 12, "D" , "11,5",
            16, "R" , "11,5", 17, "N" , "11,5", 20, "D" , "11,5", 21, "D" , "11,5",
            24, "PR", "11,5", 25, "N" , "11,5", 28, "D" , "11,5", 29, "D" , "11,5"
        ),
        createEmployee("Gacs Š.", "182,0",
            1, "Par", "11,5", 2, "D" , "11,5", 4, "P", "2,0", 5, "R" , "11,5", 6, "D" , "11,5",
            10, "PR", "11,5", 11, "PN", "11,5", 13, "PR", "11,5", 14, "PN", "11,5",
            16, "R" , "11,5", 17, "N" , "11,5", 20, "R" , "11,5", 21, "R" , "11,5",
            24, "PR", "11,5", 25, "PN", "11,5", 28, "PR", "11,5", 29, "PN", "11,5"
        ),
        createEmployee("Bc. Bariš D.", "182,0",
            1, "R" , "11,5", 2, "N" , "11,5", 4, "P", "2,0", 5, "N" , "11,5", 6, "Par", "11,5",
            9, "PR", "11,5", 10, "N" , "11,5", 13, "R" , "11,5", 14, "N" , "11,5",
            17, "PR", "11,5", 18, "PN", "11,5", 20, "PR", "11,5", 21, "PN", "11,5",
            24, "R" , "11,5", 28, "D" , "11,5", 29, "D" , "11,5"
        ),
        createEmployee("Sautner R.", "174,5",
            1, "D" , "11,5", 2, "N" , "11,5", 4, "P", "2,0", 5, "D" , "11,5", 6, "D" , "11,5",
            9, "P" , "11,5", 10, "N" , "11,5", 13, "R" , "11,5", 14, "N" , "11,5",
            17, "PR", "11,5", 18, "PN", "11,5", 20, "PR", "11,5", 21, "PN", "11,5",
            24, "PR", "11,5", 28, "PR", "11,5", 29, "PN", "11,5", 30, "N" , "11,5"
        ),
        createEmployee("Kabát M.", "182,0",
            1, "PR", "11,5", 2, "PN", "11,5", 4, "P", "2,0", 5, "PR", "11,5", 6, "PN", "11,5",
            9, "P" , "11,5", 10, "R" , "11,5", 12, "N" , "11,5", 16, "D" , "11,5",
            17, "D" , "11,5", 20, "D" , "11,5", 21, "N" , "11,5", 24, "R" , "11,5",
            25, "N" , "11,5", 26, "N" , "11,5", 29, "N" , "11,5"
        ),
        createEmployee("Kinčok K.", "174,5",
            1, "PR", "11,5", 2, "PN", "11,5", 4, "P", "2,0", 5, "PR", "11,5", 6, "PN", "11,5",
            9, "PR", "11,5", 10, "PR", "11,5", 11, "N" , "11,5", 13, "PR", "11,5",
            14, "PN", "11,5", 16, "R" , "11,5", 17, "R" , "11,5", 20, "R" , "11,5",
            21, "N" , "11,5", 24, "R" , "11,5", 28, "D" , "11,5"
        ),
        createEmployee("Krenčan M.", "182,0",
            2, "SR", "11,5", 3, "SN", "11,5", 4, "P", "2,0", 7, "SR", "11,5", 8, "SN", "11,5",
            11, "P" , "7,5", 12, "D" , "11,5", 15, "SR", "11,5", 16, "SN", "11,5",
            19, "SR", "11,5", 20, "SN", "11,5", 23, "SR", "11,5", 24, "SN", "11,5",
            27, "SR", "11,5", 28, "SN", "11,5", 31, "SR", "11,5"
        ),
        createEmployee("Nemec M.", "174,5",
            1, "PR", "11,5", 2, "PN", "11,5", 3, "P", "2,0", 6, "KZ", "11,5", 7, "KZ", "11,5",
            9, "R" , "11,5", 10, "R" , "11,5", 11, "PN", "11,5", 15, "PR", "11,5",
            16, "PN", "11,5", 18, "R" , "11,5", 19, "N" , "11,5", 21, "PR", "11,5",
            22, "PN", "11,5", 29, "D" , "11,5", 30, "D" , "11,5"
        ),
        createEmployee("Rieger T.", "174,5",
            1, "Par", "11,5", 2, "PN", "11,5", 3, "P", "2,0", 8, "N" , "11,5",
            10, "R" , "11,5", 11, "PN", "11,5", 14, "PR", "11,5", 15, "PN", "11,5",
            16, "N" , "11,5", 18, "PR", "11,5", 19, "N" , "11,5", 21, "PR", "11,5",
            22, "PN", "11,5", 27, "N" , "11,5", 29, "N" , "11,5", 30, "N" , "11,5"
        ),
        createEmployee("Lukáč Martin", "174,5",
            1, "R" , "11,5", 2, "N" , "11,5", 3, "P", "2,0", 6, "PR", "11,5", 7, "PN", "11,5",
            10, "D" , "11,5", 14, "D" , "11,5", 15, "N" , "11,5", 16, "D" , "11,5",
            17, "KZS", "11,5", 18, "PN", "11,5", 21, "KZS", "11,5", 22, "N" , "11,5",
            26, "PR", "11,5", 27, "PN", "11,5", 29, "PR", "11,5", 30, "N" , "11,5"
        ),
        createEmployee("Szelényi L.", "182,0",
            1, "R" , "11,5", 2, "N" , "11,5", 3, "P", "2,0", 6, "PR", "11,5", 7, "PN", "11,5",
            9, "PR", "11,5", 10, "R" , "11,5", 13, "N" , "11,5", 15, "R" , "11,5",
            16, "KZS", "11,5", 17, "PN", "11,5", 18, "R" , "11,5", 21, "KZS", "11,5",
            22, "N" , "11,5", 25, "PR", "11,5", 26, "PN", "11,5", 29, "PR", "11,5",
            30, "N" , "11,5"
        ),
        createEmployee("test", "0")
    ))

    // Footer summary row counts (computed dynamically would be nice, but keep static default fallback or update dynamically!)
    val rannaCounts = listOf(
        2.0, 2.0, 3.0, 1.0, 2.0, 1.0, 2.0,
        1.0, 1.0, 1.5, 3.0, 1.0, 2.0, 2.0,
        2.0, 2.0, 2.0, 3.0, 3.0, 2.0, 2.0,
        2.0, 2.0, 2.0, 2.0, 3.0, 1.0, 1.0,
        2.0, 1.0, 1.0
    )

    val nocnaCounts = listOf(
        2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0,
        1.0, 2.0, 1.0, 2.0, 1.0, 2.0, 1.0,
        2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0,
        2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0,
        2.0, 2.0, 3.0
    )

    // Recalculates dynamically the day patrol counts (denné hliadky) based on Smeny 1,2,3,4
    fun getDynamicDayPatrolCounts(): List<Double> {
        val ym = getYearMonthForIndex(activeRosterMonth)
        val numDays = ym.lengthOfMonth()
        val list = MutableList(numDays) { 0.0 }
        val rannaCodes = setOf("SR", "PR", "R")
        val combined = topEmployees + bottomEmployees
        
        // Separating "Denná skupina" and "Smeny 1,2,3,4" based on the first shift leader index
        val firstLeaderIdx = combined.indexOfFirst { emp ->
            val nameLower = emp.name.lowercase()
            nameLower.contains("bielik") ||
            nameLower.contains("kelo") ||
            (nameLower.contains("krenčan") && !nameLower.contains("mgr")) ||
            (nameLower.contains("obert") && !nameLower.contains("oberfranc"))
        }

        val shiftEmployees = if (firstLeaderIdx >= 0) {
            combined.drop(firstLeaderIdx)
        } else {
            bottomEmployees
        }

        for (day in 1..numDays) {
            var activeCount = 0
            shiftEmployees.forEach { emp ->
                val code = emp.shifts[day]?.code?.trim()?.uppercase()
                if (code != null && rannaCodes.contains(code)) {
                    activeCount++
                }
            }
            list[day - 1] = activeCount / 2.0
        }
        return list
    }

    // Recalculates dynamically the night patrol counts (nočné hliadky) based on Smeny 1,2,3,4
    fun getDynamicNightPatrolCounts(): List<Double> {
        val ym = getYearMonthForIndex(activeRosterMonth)
        val numDays = ym.lengthOfMonth()
        val list = MutableList(numDays) { 0.0 }
        val nocnaCodes = setOf("SN", "PN", "N")
        val combined = topEmployees + bottomEmployees
        
        // Separating "Denná skupina" and "Smeny 1,2,3,4" based on the first shift leader index
        val firstLeaderIdx = combined.indexOfFirst { emp ->
            val nameLower = emp.name.lowercase()
            nameLower.contains("bielik") ||
            nameLower.contains("kelo") ||
            (nameLower.contains("krenčan") && !nameLower.contains("mgr")) ||
            (nameLower.contains("obert") && !nameLower.contains("oberfranc"))
        }

        val shiftEmployees = if (firstLeaderIdx >= 0) {
            combined.drop(firstLeaderIdx)
        } else {
            bottomEmployees
        }

        for (day in 1..numDays) {
            var activeCount = 0
            shiftEmployees.forEach { emp ->
                val code = emp.shifts[day]?.code?.trim()?.uppercase()
                if (code != null && nocnaCodes.contains(code)) {
                    activeCount++
                }
            }
            list[day - 1] = activeCount / 2.0
        }
        return list
    }

    // Recalculates dynamically the patrol counts (Hliadky) based on both Ranna and Nocna sum
    fun getDynamicPatrolCounts(): List<Double> {
        val dayCounts = getDynamicDayPatrolCounts()
        val nightCounts = getDynamicNightPatrolCounts()
        val ym = getYearMonthForIndex(activeRosterMonth)
        val numDays = ym.lengthOfMonth()
        return List(numDays) { dayCounts[it] + nightCounts[it] }
    }

    fun getJuneTopEmployees(): List<RosterEmployee> {
        return listOf(
            createEmployee("PaedDr. Adámik M.", "165",
                1, "P", "7,5", 2, "P", "7,5", 3, "P", "7,5", 4, "P", "7,5", 5, "P", "7,5",
                8, "P", "7,5", 9, "P", "7,5", 10, "P", "7,5", 11, "P", "7,5", 12, "P", "7,5",
                15, "P", "7,5", 16, "P", "7,5", 17, "P", "7,5", 18, "P", "7,5", 19, "P", "7,5",
                22, "P", "7,5", 23, "P", "7,5", 24, "P", "7,5", 25, "P", "7,5", 26, "P", "7,5",
                29, "P", "7,5", 30, "P", "7,5"
            ),
            createEmployee("Bc. Kovančík", "165",
                1, "P", "7,5", 2, "P", "7,5", 3, "P", "7,5", 4, "P", "7,5", 5, "P", "7,5",
                8, "P", "7,5", 9, "D", "7,5", 10, "D", "7,5", 11, "D", "7,5", 12, "D", "7,5",
                15, "D", "7,5", 16, "P", "7,5", 17, "P", "7,5", 18, "P", "7,5", 19, "P", "7,5",
                22, "P", "7,5", 23, "P", "7,5", 24, "P", "7,5", 25, "P", "7,5", 26, "P", "7,5",
                29, "P", "7,5", 30, "P", "7,5"
            ),
            createEmployee("Kováčová R.", "165",
                1, "P", "7,5", 2, "P", "7,5", 3, "P", "7,5", 4, "P", "7,5", 5, "P", "7,5",
                8, "P", "7,5", 9, "P", "7,5", 10, "P", "7,5", 11, "P", "7,5", 12, "P", "7,5",
                15, "D", "7,5", 16, "D", "7,5", 17, "D", "7,5", 18, "D", "7,5", 19, "D", "7,5",
                22, "D", "7,5", 23, "D", "7,5", 24, "D", "7,5", 25, "D", "7,5", 26, "D", "7,5",
                29, "D", "7,5", 30, "P", "7,5"
            ),
            createEmployee("Bc. Adámik R.", "165",
                1, "CH", "7,5", 2, "CH", "7,5", 3, "CH", "7,5", 4, "CH", "7,5", 5, "CH", "7,5",
                8, "CH", "7,5", 9, "CH", "7,5", 10, "CH", "7,5", 11, "CH", "7,5", 12, "CH", "7,5",
                15, "CH", "7,5", 16, "CH", "7,5", 17, "CH", "7,5", 18, "CH", "7,5", 19, "CH", "7,5",
                22, "CH", "7,5", 23, "CH", "7,5", 24, "CH", "7,5",
                25, "P", "7,5", 26, "P", "7,5", 29, "P", "7,5", 30, "P", "7,5"
            ),
            createEmployee("Roštáš A.", "169",
                1, "P", "7,5", 2, "P", "7,5", 3, "P", "7,5", 4, "P", "7,5", 5, "P", "7,5",
                8, "P", "7,5", 9, "P", "7,5", 10, "D", "7,5", 11, "D", "7,5", 12, "D", "7,5",
                15, "D", "7,5", 16, "P", "7,5", 17, "P", "7,5", 18, "P", "7,5", 19, "P", "7,5",
                22, "P", "7,5", 23, "P", "7,5", 24, "P", "7,5", 25, "SR", "11,5", 26, "P", "7,5",
                29, "P", "7,5", 30, "P", "7,5"
            ),
            createEmployee("Bc. Peťko M.", "169",
                1, "P", "7,5", 2, "P", "7,5", 3, "P", "7,5", 4, "P", "7,5", 5, "P", "7,5",
                8, "P", "7,5", 9, "P", "7,5", 10, "D", "7,5", 11, "D", "7,5", 12, "D", "7,5",
                15, "D", "7,5", 16, "SR", "11,5", 17, "P", "7,5", 18, "P", "7,5", 19, "P", "7,5",
                22, "P", "7,5", 23, "P", "7,5", 24, "P", "7,5", 25, "P", "7,5", 26, "P", "7,5",
                29, "P", "7,5", 30, "P", "7,5"
            ),
            createEmployee("Bc. Šebelédy", "173",
                1, "P", "7,5", 2, "P", "7,5", 3, "P", "7,5", 4, "P", "7,5", 5, "P", "7,5",
                8, "P", "7,5", 9, "P", "7,5", 10, "P", "7,5", 11, "P", "7,5", 12, "P", "7,5",
                15, "SR", "11,5", 16, "P", "7,5", 17, "P", "7,5", 18, "P", "7,5", 19, "P", "7,5",
                22, "P", "7,5", 23, "P", "7,5", 24, "P", "7,5", 25, "P", "7,5", 26, "SR", "11,5",
                29, "P", "7,5", 30, "P", "7,5"
            ),
            createEmployee("Bc. Töröková", "173",
                1, "P", "7,5", 2, "P", "7,5", 3, "P", "7,5", 4, "P", "7,5", 5, "SR", "11,5",
                8, "P", "7,5", 9, "P", "7,5", 10, "P", "7,5", 11, "P", "7,5", 12, "P", "7,5",
                15, "P", "7,5", 16, "P", "7,5", 17, "P", "7,5", 18, "P", "7,5", 19, "P", "7,5",
                22, "P", "7,5", 23, "P", "7,5", 24, "P", "7,5", 25, "P", "7,5", 26, "P", "7,5",
                29, "SR", "11,5", 30, "P", "7,5"
            ),
            createEmployee("Bielik R.", "168",
                1, "SR", "11,5", 2, "SN", "11,5", 3, "P", "2,0", 5, "SN", "11,5",
                9, "SR", "11,5", 10, "SN", "11,5", 13, "SR", "11,5", 14, "SN", "11,5",
                17, "SR", "11,5", 18, "SN", "11,5", 19, "V", "5,0", 21, "SR", "11,5",
                22, "SN", "11,5", 25, "SR", "11,5", 26, "SN", "11,5", 29, "SR", "11,5",
                30, "SN", "11,5"
            ),
            createEmployee("Fábry J.", "168",
                1, "R", "11,5", 2, "N", "11,5", 3, "P", "2,0", 4, "PR", "11,5", 5, "PN", "11,5",
                9, "N", "11,5", 13, "R", "11,5", 14, "N", "11,5", 17, "R", "11,5", 18, "N", "11,5",
                19, "V", "5,0", 21, "PR", "11,5", 22, "N", "11,5", 25, "D", "11,5", 26, "N", "11,5",
                29, "PR", "11,5", 30, "N", "11,5"
            ),
            createEmployee("Kazár D.", "168",
                1, "R", "11,5", 2, "N", "11,5", 3, "P", "2,0", 5, "PR", "11,5", 6, "PN", "11,5",
                9, "N", "11,5", 13, "R", "11,5", 14, "N", "11,5", 17, "R", "11,5", 18, "N", "11,5",
                19, "V", "5,0", 21, "PR", "11,5", 22, "PN", "11,5", 25, "PR", "11,5", 26, "PN", "11,5",
                29, "PR", "11,5", 30, "N", "11,5"
            ),
            createEmployee("PhDr. Kabát", "168",
                1, "PR", "11,5", 2, "PN", "11,5", 3, "P", "2,0", 4, "D", "11,5", 5, "D", "11,5", 6, "D", "11,5",
                9, "R", "11,5", 10, "N", "11,5", 13, "R", "11,5", 14, "N", "11,5", 15, "D", "11,5", 16, "D", "11,5",
                17, "PR", "11,5", 18, "PN", "11,5", 19, "V", "5,0", 21, "D", "11,5", 25, "PR", "11,5", 26, "PN", "11,5",
                29, "R", "11,5", 30, "N", "11,5"
            ),
            createEmployee("Oberfranc R.", "168",
                1, "PR", "11,5", 2, "PN", "11,5", 3, "P", "2,0", 5, "R", "11,5", 6, "N", "11,5",
                9, "R", "11,5", 10, "N", "11,5", 13, "R", "11,5", 14, "N", "11,5", 17, "PR", "11,5", 18, "PN", "11,5",
                19, "V", "5,0", 21, "R", "11,5", 22, "N", "11,5", 25, "PR", "11,5", 26, "PN", "11,5",
                29, "R", "11,5", 30, "N", "11,5"
            ),
            createEmployee("Polyák A.", "168",
                2, "R", "11,5", 3, "P", "2,0", 4, "D", "11,5", 5, "D", "11,5", 6, "PR", "11,5", 7, "PN", "11,5",
                10, "R", "11,5", 11, "N", "11,5", 12, "D", "11,5", 13, "D", "11,5", 14, "PR", "11,5", 15, "PN", "11,5",
                18, "R", "11,5", 19, "V", "5,0", 22, "PR", "11,5", 23, "PN", "11,5", 26, "R", "11,5", 27, "N", "11,5",
                30, "PR", "11,5"
            ),
            createEmployee("Bc. Petráš A.", "168",
                2, "R", "11,5", 3, "P", "2,0", 4, "D", "11,5", 5, "D", "11,5", 6, "PR", "11,5", 7, "PN", "11,5",
                10, "R", "11,5", 11, "N", "11,5", 12, "D", "11,5", 13, "D", "11,5", 14, "PR", "11,5", 15, "PN", "11,5",
                18, "R", "11,5", 19, "V", "5,0", 22, "PR", "11,5", 23, "PN", "11,5", 26, "R", "11,5", 27, "N", "11,5",
                30, "PR", "11,5"
            ),
            createEmployee("Bc. Kelo R.", "168",
                3, "SR", "11,5", 4, "SN", "11,5", 3, "P", "2,0", 5, "D", "11,5", 6, "D", "11,5", 7, "D", "11,5", 8, "D", "11,5",
                11, "D", "11,5", 12, "D", "11,5", 15, "SR", "11,5", 16, "SN", "11,5", 19, "V", "5,0",
                23, "SR", "11,5", 24, "SN", "11,5", 27, "SR", "11,5", 28, "SN", "11,5"
            ),
            createEmployee("Sautner R.", "168",
                3, "SR", "11,5", 4, "SN", "11,5", 3, "P", "2,0", 5, "D", "11,5", 6, "D", "11,5", 7, "D", "11,5", 8, "D", "11,5",
                11, "D", "11,5", 12, "D", "11,5", 15, "SR", "11,5", 16, "SN", "11,5", 19, "V", "5,0",
                23, "SR", "11,5", 24, "SN", "11,5", 27, "SR", "11,5", 28, "SN", "11,5"
            ),
            createEmployee("Andruška Z.", "168",
                1, "D", "11,5", 2, "D", "11,5", 3, "P", "2,0", 4, "R", "11,5", 5, "N", "11,5", 6, "D", "11,5",
                8, "PR", "11,5", 9, "PN", "11,5", 12, "R", "11,5", 13, "N", "11,5", 16, "PR", "11,5", 17, "PN", "11,5",
                18, "R", "11,5", 19, "V", "5,0", 20, "PR", "11,5", 21, "PN", "11,5", 24, "R", "11,5", 25, "N", "11,5",
                28, "PR", "11,5", 29, "PN", "11,5"
            ),
            createEmployee("Bc. Masaryk", "168",
                1, "PR", "11,5", 2, "PN", "11,5", 3, "P", "2,0", 5, "PR", "11,5", 6, "PN", "11,5",
                9, "R", "11,5", 10, "N", "11,5", 13, "R", "11,5", 14, "N", "11,5", 17, "PR", "11,5", 18, "PN", "11,5",
                19, "V", "5,0", 21, "R", "11,5", 22, "N", "11,5", 24, "D", "11,5", 25, "D", "11,5", 29, "D", "11,5",
                30, "PN", "11,5"
            ),
            createEmployee("Šovčík M.", "168",
                1, "PR", "11,5", 2, "PN", "11,5", 3, "P", "2,0", 5, "PR", "11,5", 6, "PN", "11,5",
                9, "R", "11,5", 10, "N", "11,5", 13, "R", "11,5", 14, "N", "11,5", 17, "PR", "11,5", 18, "PN", "11,5",
                19, "V", "5,0", 21, "R", "11,5", 22, "N", "11,5", 24, "D", "11,5", 25, "D", "11,5", 29, "D", "11,5",
                30, "PN", "11,5"
            )
        )
    }

    fun getJuneBottomEmployees(): List<RosterEmployee> {
        return listOf(
            createEmployee("Bc. Obert L.", "163",
                2, "SR", "11,5", 3, "SN", "13,5", 6, "SR", "11,5", 7, "SN", "11,5",
                10, "SR", "11,5", 11, "SN", "11,5", 14, "SR", "11,5", 15, "SN", "11,5",
                18, "SR", "11,5", 19, "V", "5,0", 21, "D", "11,5", 22, "SR", "11,5",
                23, "SN", "11,5", 26, "SR", "11,5", 27, "SN", "11,5", 28, "D", "11,5",
                30, "SR", "11,5"
            ),
            createEmployee("Adámik P.", "163",
                2, "SR", "11,5", 3, "SN", "13,5", 6, "SR", "11,5", 7, "SN", "11,5",
                10, "SR", "11,5", 11, "SN", "11,5", 13, "D", "11,5", 14, "SR", "11,5",
                15, "SN", "11,5", 18, "SR", "11,5", 19, "V", "5,0", 21, "D", "11,5",
                22, "SR", "11,5", 23, "SN", "11,5", 26, "SR", "11,5", 27, "SN", "11,5",
                28, "D", "11,5", 30, "SR", "11,5"
            ),
            createEmployee("Gacs Š.", "163",
                2, "SR", "11,5", 3, "SN", "13,5", 6, "SR", "11,5", 7, "SN", "11,5",
                10, "SR", "11,5", 11, "SN", "11,5", 14, "SR", "11,5", 15, "SN", "11,5",
                18, "SR", "11,5", 19, "V", "5,0", 21, "D", "11,5", 22, "SR", "11,5",
                23, "SN", "11,5", 26, "SR", "11,5", 27, "SN", "11,5", 28, "D", "11,5",
                30, "SR", "11,5"
            ),
            createEmployee("Kabát M.", "163",
                3, "PR", "11,5", 4, "PN", "11,5", 7, "PR", "11,5", 8, "D", "11,5",
                11, "PR", "11,5", 12, "PN", "11,5", 15, "PR", "11,5", 16, "PN", "11,5",
                19, "V", "5,0", 20, "PR", "11,5", 21, "PN", "11,5", 22, "D", "11,5",
                24, "PR", "11,5", 25, "PN", "11,5", 26, "D", "11,5", 28, "PR", "11,5",
                29, "PN", "11,5"
            ),
            createEmployee("Kinčok K.", "163",
                3, "PR", "11,5", 4, "PN", "11,5", 7, "PR", "11,5", 8, "D", "11,5",
                11, "PR", "11,5", 12, "PN", "11,5", 15, "PR", "11,5", 16, "PN", "11,5",
                19, "V", "5,0", 20, "PR", "11,5", 21, "PN", "11,5", 22, "D", "11,5",
                24, "PR", "11,5", 25, "PN", "11,5", 26, "D", "11,5", 28, "PR", "11,5",
                29, "PN", "11,5"
            ),
            createEmployee("Bc. Bariš D.", "163",
                3, "PR", "11,5", 4, "PN", "11,5", 7, "PR", "11,5", 8, "D", "11,5",
                11, "PR", "11,5", 12, "PN", "11,5", 15, "PR", "11,5", 16, "PN", "11,5",
                19, "V", "5,0", 20, "PR", "11,5", 21, "PN", "11,5", 22, "D", "11,5",
                24, "PR", "11,5", 25, "PN", "11,5", 26, "D", "11,5", 28, "PR", "11,5",
                29, "PN", "11,5"
            ),
            createEmployee("Mgr. Krenčan", "169",
                1, "P", "7,5", 2, "P", "7,5", 3, "P", "7,5", 4, "P", "7,5", 5, "P", "7,5",
                8, "P", "7,5", 9, "P", "7,5", 10, "P", "7,5", 11, "P", "7,5", 12, "P", "7,5",
                15, "P", "7,5", 16, "P", "7,5", 17, "P", "7,5", 18, "P", "7,5", 19, "V", "5,0",
                21, "SR", "11,5", 22, "P", "7,5", 23, "D", "7,5", 24, "D", "7,5", 25, "D", "7,5",
                26, "P", "7,5", 29, "D", "7,5", 30, "D", "7,5"
            ),
            createEmployee("Krenčan M.", "166",
                1, "SN", "11,5", 4, "SR", "11,5", 5, "SN", "11,5", 8, "SR", "11,5", 9, "SN", "11,5",
                12, "D", "11,5", 13, "SN", "11,5", 16, "SR", "11,5", 17, "SN", "11,5", 19, "V", "5,0",
                20, "SR", "11,5", 21, "SN", "11,5", 24, "D", "11,5", 25, "SN", "11,5", 28, "D", "11,5",
                29, "SN", "11,5"
            ),
            createEmployee("Nemec M.", "166",
                1, "SN", "11,5", 4, "SR", "11,5", 5, "SN", "11,5", 8, "SR", "11,5", 9, "SN", "11,5",
                12, "D", "11,5", 13, "SN", "11,5", 16, "SR", "11,5", 17, "SN", "11,5", 19, "V", "5,0",
                20, "SR", "11,5", 21, "SN", "11,5", 24, "D", "11,5", 25, "SN", "11,5", 28, "D", "11,5",
                29, "SN", "11,5"
            ),
            createEmployee("Rieger T.", "166",
                1, "SN", "11,5", 4, "SR", "11,5", 5, "SN", "11,5", 8, "SR", "11,5", 9, "SN", "11,5",
                12, "D", "11,5", 13, "SN", "11,5", 16, "SR", "11,5", 17, "SN", "11,5", 19, "V", "5,0",
                20, "SR", "11,5", 21, "SN", "11,5", 24, "D", "11,5", 25, "SN", "11,5", 28, "D", "11,5",
                29, "SN", "11,5"
            ),
            createEmployee("Lukáč Martin", "166",
                1, "SN", "11,5", 4, "SR", "11,5", 5, "SN", "11,5", 8, "SR", "11,5", 9, "SN", "11,5",
                12, "D", "11,5", 13, "SN", "11,5", 16, "SR", "11,5", 17, "SN", "11,5", 19, "V", "5,0",
                20, "SR", "11,5", 21, "SN", "11,5", 24, "D", "11,5", 25, "SN", "11,5", 28, "D", "11,5",
                29, "SN", "11,5"
            ),
            createEmployee("Szelényi L.", "166",
                1, "SN", "11,5", 4, "SR", "11,5", 5, "SN", "11,5", 8, "SR", "11,5", 9, "SN", "11,5",
                12, "D", "11,5", 13, "SN", "11,5", 16, "SR", "11,5", 17, "SN", "11,5", 19, "V", "5,0",
                20, "SR", "11,5", 21, "SN", "11,5", 24, "D", "11,5", 25, "SN", "11,5", 28, "D", "11,5",
                29, "SN", "11,5"
            )
        )
    }

    private fun createEmployee(name: String, total: String, vararg shifts: Any): RosterEmployee {
        val completeShifts = mutableMapOf<Int, RosterCell>()
        // Pre-populate all 31 days with empty shifts
        for (day in 1..31) {
            completeShifts[day] = RosterCell(day, null, null)
        }
        
        var i = 0
        while (i < shifts.size) {
            val day = shifts[i] as Int
            val code = shifts[i + 1] as String
            val hours = shifts[i + 2] as String
            completeShifts[day] = RosterCell(day, code, hours)
            i += 3
        }
        return RosterEmployee(name, total, completeShifts)
    }

    // Recalculate hours dynamically for an employee
    fun recalculateHours(shifts: Map<Int, RosterCell>): String {
        var total = 0.0
        shifts.values.forEach { cell ->
            cell.hours?.let { hStr ->
                val cleaned = hStr.replace(',', '.').toDoubleOrNull()
                if (cleaned != null) {
                    total += cleaned
                }
            }
        }
        if (total % 1.0 == 0.0) {
            return total.toInt().toString()
        } else {
            return String.format(java.util.Locale.US, "%.1f", total).replace('.', ',')
        }
    }

    // Update specific day shift for employee
    fun updateCell(employeeName: String, day: Int, code: String?, hours: String?) {
        val inTop = topEmployees.any { it.name == employeeName }
        val normalizedCode = if (code?.isBlank() == true || code == "NONE" || code == "Voľno") null else code
        val normalizedHours = if (normalizedCode == null) null else hours

        if (inTop) {
            topEmployees = topEmployees.map { emp ->
                if (emp.name == employeeName) {
                    val newShifts = emp.shifts.toMutableMap()
                    newShifts[day] = RosterCell(day, normalizedCode, normalizedHours)
                    val calculatedTotal = recalculateHours(newShifts)
                    emp.copy(shifts = newShifts, totalHours = calculatedTotal)
                } else emp
            }
            monthlyTopEmployees[activeRosterMonth] = topEmployees
            if (activeRosterMonth == 0) {
                decemberTopEmployees = topEmployees
            } else if (activeRosterMonth == 1) {
                nextMonthTopEmployees = topEmployees
            }
        } else {
            bottomEmployees = bottomEmployees.map { emp ->
                if (emp.name == employeeName) {
                    val newShifts = emp.shifts.toMutableMap()
                    newShifts[day] = RosterCell(day, normalizedCode, normalizedHours)
                    val calculatedTotal = recalculateHours(newShifts)
                    emp.copy(shifts = newShifts, totalHours = calculatedTotal)
                } else emp
            }
            monthlyBottomEmployees[activeRosterMonth] = bottomEmployees
            if (activeRosterMonth == 0) {
                decemberBottomEmployees = bottomEmployees
            } else if (activeRosterMonth == 1) {
                nextMonthBottomEmployees = bottomEmployees
            }
        }
        onCellUpdatedExternal?.invoke(employeeName, day, normalizedCode, normalizedHours)
        saveCurrentState()
    }

    // Update specific day shift for employee in a particular month index (0 = December 2025, 1 = January 2026, 6 = June 2026, etc)
    fun updateCellForMonth(monthIndex: Int, employeeName: String, day: Int, code: String?, hours: String?) {
        val oldMonth = activeRosterMonth
        if (monthIndex != oldMonth) {
            // Save state of oldMonth
            monthlyTopEmployees[oldMonth] = topEmployees
            monthlyBottomEmployees[oldMonth] = bottomEmployees
            
            activeRosterMonth = monthIndex
            val loadedTop = monthlyTopEmployees[monthIndex]
            val loadedBottom = monthlyBottomEmployees[monthIndex]
            if (loadedTop != null && loadedBottom != null) {
                topEmployees = loadedTop
                bottomEmployees = loadedBottom
            } else {
                if (monthIndex == 0) {
                    topEmployees = decemberTopEmployees
                    bottomEmployees = decemberBottomEmployees
                } else {
                    topEmployees = nextMonthTopEmployees
                    bottomEmployees = nextMonthBottomEmployees
                }
            }
        }

        updateCell(employeeName, day, code, hours)

        if (monthIndex != oldMonth) {
            // Save state of monthIndex
            monthlyTopEmployees[monthIndex] = topEmployees
            monthlyBottomEmployees[monthIndex] = bottomEmployees
            
            activeRosterMonth = oldMonth
            val loadedTop = monthlyTopEmployees[oldMonth]
            val loadedBottom = monthlyBottomEmployees[oldMonth]
            if (loadedTop != null && loadedBottom != null) {
                topEmployees = loadedTop
                bottomEmployees = loadedBottom
            } else {
                if (oldMonth == 0) {
                    topEmployees = decemberTopEmployees
                    bottomEmployees = decemberBottomEmployees
                } else {
                    topEmployees = nextMonthTopEmployees
                    bottomEmployees = nextMonthBottomEmployees
                }
            }
        }
    }

    // Move/Copy specific shift to another day for employee
    fun moveOrCopyShift(employeeName: String, fromDay: Int, toDay: Int, deleteSource: Boolean) {
        val combined = topEmployees + bottomEmployees
        val employee = combined.find { it.name == employeeName } ?: return
        val sourceCell = employee.shifts[fromDay] ?: return

        updateCell(employeeName, toDay, sourceCell.code, sourceCell.hours)
        if (deleteSource) {
            updateCell(employeeName, fromDay, null, null)
        }
    }

    // Change/Rename employee name dialog support
    fun renameEmployee(oldName: String, newName: String) {
        if (newName.isBlank()) return
        initMonths()
        val inTop = topEmployees.any { it.name == oldName }
        if (inTop) {
            topEmployees = topEmployees.map { emp ->
                if (emp.name == oldName) emp.copy(name = newName) else emp
            }
        } else {
            bottomEmployees = bottomEmployees.map { emp ->
                if (emp.name == oldName) emp.copy(name = newName) else emp
            }
        }
        decemberTopEmployees = decemberTopEmployees.map { emp ->
            if (emp.name == oldName) emp.copy(name = newName) else emp
        }
        decemberBottomEmployees = decemberBottomEmployees.map { emp ->
            if (emp.name == oldName) emp.copy(name = newName) else emp
        }
        nextMonthTopEmployees = nextMonthTopEmployees.map { emp ->
            if (emp.name == oldName) emp.copy(name = newName) else emp
        }
        nextMonthBottomEmployees = nextMonthBottomEmployees.map { emp ->
            if (emp.name == oldName) emp.copy(name = newName) else emp
        }
        saveCurrentState()
    }

    // Delete employee
    fun deleteEmployee(name: String) {
        initMonths()
        topEmployees = topEmployees.filter { it.name != name }
        bottomEmployees = bottomEmployees.filter { it.name != name }
        monthlyTopEmployees[activeRosterMonth] = topEmployees
        monthlyBottomEmployees[activeRosterMonth] = bottomEmployees
        decemberTopEmployees = decemberTopEmployees.filter { it.name != name }
        decemberBottomEmployees = decemberBottomEmployees.filter { it.name != name }
        nextMonthTopEmployees = nextMonthTopEmployees.filter { it.name != name }
        nextMonthBottomEmployees = nextMonthBottomEmployees.filter { it.name != name }
        saveCurrentState()
    }

    // Add new employee
    fun addEmployee(name: String, isTop: Boolean) {
        if (name.isBlank()) return
        initMonths()
        val ym = getYearMonthForIndex(activeRosterMonth)
        val emptyShifts = mutableMapOf<Int, RosterCell>()
        for (day in 1..ym.lengthOfMonth()) {
            emptyShifts[day] = RosterCell(day, null, null)
        }
        val newEmp = RosterEmployee(name, "0", emptyShifts)
        if (isTop) {
            topEmployees = topEmployees + newEmp
        } else {
            bottomEmployees = bottomEmployees + newEmp
        }
        monthlyTopEmployees[activeRosterMonth] = topEmployees
        monthlyBottomEmployees[activeRosterMonth] = bottomEmployees
        if (decemberTopEmployees.isNotEmpty() && isTop && !decemberTopEmployees.any { it.name == name }) {
            decemberTopEmployees = decemberTopEmployees + newEmp
        } else if (decemberBottomEmployees.isNotEmpty() && !isTop && !decemberBottomEmployees.any { it.name == name }) {
            decemberBottomEmployees = decemberBottomEmployees + newEmp
        }
        if (nextMonthTopEmployees.isNotEmpty() && isTop && !nextMonthTopEmployees.any { it.name == name }) {
            nextMonthTopEmployees = nextMonthTopEmployees + newEmp
        } else if (nextMonthBottomEmployees.isNotEmpty() && !isTop && !nextMonthBottomEmployees.any { it.name == name }) {
            nextMonthBottomEmployees = nextMonthBottomEmployees + newEmp
        }
        saveCurrentState()
    }

    // Move employee up/down inside top or bottom, crossing boundaries if necessary
    fun moveEmployee(name: String, up: Boolean) {
        initMonths()
        val topList = topEmployees.toMutableList()
        val bottomList = bottomEmployees.toMutableList()
        
        val topIndex = topList.indexOfFirst { it.name == name }
        val bottomIndex = bottomList.indexOfFirst { it.name == name }
        
        if (topIndex != -1) {
            if (up) {
                if (topIndex > 0) {
                    val temp = topList[topIndex]
                    topList[topIndex] = topList[topIndex - 1]
                    topList[topIndex - 1] = temp
                    topEmployees = topList
                }
            } else {
                if (topIndex < topList.size - 1) {
                    val temp = topList[topIndex]
                    topList[topIndex] = topList[topIndex + 1]
                    topList[topIndex + 1] = temp
                    topEmployees = topList
                } else {
                    val emp = topList.removeAt(topIndex)
                    bottomList.add(0, emp)
                    topEmployees = topList
                    bottomEmployees = bottomList
                }
            }
        } else if (bottomIndex != -1) {
            if (up) {
                if (bottomIndex > 0) {
                    val temp = bottomList[bottomIndex]
                    bottomList[bottomIndex] = bottomList[bottomIndex - 1]
                    bottomList[bottomIndex - 1] = temp
                    bottomEmployees = bottomList
                } else {
                    val emp = bottomList.removeAt(bottomIndex)
                    topList.add(emp)
                    topEmployees = topList
                    bottomEmployees = bottomList
                }
            } else {
                if (bottomIndex < bottomList.size - 1) {
                    val temp = bottomList[bottomIndex]
                    bottomList[bottomIndex] = bottomList[bottomIndex + 1]
                    bottomList[bottomIndex + 1] = temp
                    bottomEmployees = bottomList
                }
            }
        }
        
        // Sync position to the current backups
        monthlyTopEmployees[activeRosterMonth] = topEmployees
        monthlyBottomEmployees[activeRosterMonth] = bottomEmployees
        if (activeRosterMonth == 0) {
            decemberTopEmployees = topEmployees
            decemberBottomEmployees = bottomEmployees
        } else if (activeRosterMonth == 1) {
            nextMonthTopEmployees = topEmployees
            nextMonthBottomEmployees = bottomEmployees
        }
        saveCurrentState()
    }

    // Returns a nice color scheme mapping to represent each shift cell elegantly
    fun getCellColorScheme(code: String?, isWeekend: Boolean = false, isDark: Boolean = false, isDailyGroup: Boolean = false): CellColors {
        if (isDark) {
            if (code == null) {
                val bg = if (isWeekend) Color(0xFF1E293B) else Color(0xFF151D2A)
                return CellColors(bg, Color(0xFF64748B))
            }
            return when (code) {
                // Adapted highly prominent and vibrant dark theme cells (standing out beautifully against black/dark surface)
                "D" -> CellColors(Color(0xFFFFEA00), Color(0xFF000000))      // vibrant bright security yellow as requested
                "CH" -> CellColors(Color(0xFF14532D), Color(0xFFFFFFFF))     // tmavozelená (deep forest green background, white text)
                "Par" -> CellColors(Color(0xFF06B6D4), Color(0xFF000000))    // vibrant cyan
                "SR", "PR", "R" -> CellColors(Color(0xFF86EFAC), Color(0xFF052E16)) // bledo zelená (bright/light mint green background, dark forest text)
                "SN", "PN", "N" -> CellColors(Color(0xFF3B82F6), Color(0xFFFFFFFF)) // vibrant electric blue
                "KZ", "KZS", "KZV", "KZVS" -> CellColors(Color(0xFFD946EF), Color(0xFF000000)) // bright magenta
                "P" -> {
                    if (isDailyGroup) {
                        CellColors(Color(0xFF86EFAC), Color(0xFF052E16)) // bledo zelená (same as R/SR/PR)
                    } else {
                        CellColors(Color(0xFF64748B), Color(0xFFFFFFFF))      // slate gray (porada)
                    }
                }
                "V" -> CellColors(Color(0xFF8B5A2B), Color(0xFFFFFFFF))      // brown
                else -> CellColors(Color(0xFF475569), Color(0xFFF1F5F9))
            }
        } else {
            if (code == null) {
                val bg = if (isWeekend) Color(0xFFF1F5F9) else Color(0xFFFFFFFF)
                return CellColors(bg, Color(0xFF475569))
            }
            return when (code) {
                // Highly refined, modern pastel palette for light mode (clean, easy on eyes, and premium)
                "D" -> CellColors(Color(0xFFFEF08A), Color(0xFF854D0E))      // soft pastel yellow
                "CH" -> CellColors(Color(0xFF166534), Color(0xFFF0FDF4))     // tmavozelená (rich dark green background, soft pale green text)
                "Par" -> CellColors(Color(0xFFCCFBF1), Color(0xFF0F766E))    // soft pastel teal
                "SR", "PR", "R" -> CellColors(Color(0xFFDCFCE7), Color(0xFF14532D)) // bledo zelená (light mint green background, dark forest green text)
                "SN", "PN", "N" -> CellColors(Color(0xFFDBEAFE), Color(0xFF1D4ED8)) // soft pastel blue
                "KZ", "KZS", "KZV", "KZVS" -> CellColors(Color(0xFFF3E8FF), Color(0xFF6B21A8)) // soft pastel purple
                "P" -> {
                    if (isDailyGroup) {
                        CellColors(Color(0xFFDCFCE7), Color(0xFF14532D)) // bledo zelená (same as R/SR/PR)
                    } else {
                        CellColors(Color(0xFFF1F5F9), Color(0xFF334155))      // soft clean gray (porada)
                    }
                }
                "V" -> CellColors(Color(0xFFEAD8C3), Color(0xFF5F3E1E))      // soft brown
                else -> CellColors(Color(0xFFE2E8F0), Color(0xFF475569))     // default neutral gray
            }
        }
    }
}

data class CellColors(
    val background: Color,
    val text: Color
)
