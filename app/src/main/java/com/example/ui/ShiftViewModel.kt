package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ShiftDay
import com.example.data.ShiftRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private fun safeZoneId(): java.time.ZoneId {
    return try {
        java.time.ZoneId.systemDefault()
    } catch (e: Exception) {
        java.time.ZoneId.of("UTC")
    }
}

private fun safeLocalDateNow(): LocalDate {
    return try {
        LocalDate.now(safeZoneId())
    } catch (e: Exception) {
        LocalDate.of(2026, 5, 27)
    }
}

private fun safeYearMonthNow(): YearMonth {
    return try {
        YearMonth.now(safeZoneId())
    } catch (e: Exception) {
        YearMonth.of(2026, 5)
    }
}

class ShiftViewModel(private val repository: ShiftRepository) : ViewModel() {

    init {
        viewModelScope.launch {
            try {
                repository.allShiftDays.filter { it.isNotEmpty() }.firstOrNull()?.let { currentDays ->
                    val todayStr = safeLocalDateNow().toString()
                    val toUpdate = currentDays.filter { day ->
                        day.date < todayStr && (day.note?.isNotBlank() == true || day.reminderText?.isNotBlank() == true)
                    }
                    if (toUpdate.isNotEmpty()) {
                        val updatedList = toUpdate.map { day ->
                            day.copy(note = null, reminderText = null)
                        }
                        repository.insertShiftDays(updatedList)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Active Screen View
    private val _currentView = MutableStateFlow("shichter")
    val currentView: StateFlow<String> = _currentView.asStateFlow()

    fun setCurrentView(view: String) {
        _currentView.value = view
    }

    // Settings configuration
    private val _isCountdownEnabled = MutableStateFlow(true)
    val isCountdownEnabled: StateFlow<Boolean> = _isCountdownEnabled.asStateFlow()

    fun setCountdownEnabled(enabled: Boolean) {
        _isCountdownEnabled.value = enabled
    }

    private val _themeMode = MutableStateFlow("SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
    }

    private val _defaultShiftLength = MutableStateFlow(12)
    val defaultShiftLength: StateFlow<Int> = _defaultShiftLength.asStateFlow()

    fun setDefaultShiftLength(length: Int) {
        _defaultShiftLength.value = length
    }

    private val _isCleanerModeEnabled = MutableStateFlow(false)
    val isCleanerModeEnabled: StateFlow<Boolean> = _isCleanerModeEnabled.asStateFlow()

    fun setCleanerModeEnabled(enabled: Boolean) {
        _isCleanerModeEnabled.value = enabled
    }

    private val _vacationAllowance = MutableStateFlow(25f)
    val vacationAllowance: StateFlow<Float> = _vacationAllowance.asStateFlow()

    fun setVacationAllowance(allowance: Float) {
        _vacationAllowance.value = allowance
    }

    // Share preview states
    private val _sharedPreviewShifts = MutableStateFlow<List<ShiftDay>?>(null)
    val sharedPreviewShifts: StateFlow<List<ShiftDay>?> = _sharedPreviewShifts.asStateFlow()

    private val _previewSenderName = MutableStateFlow<String?>(null)
    val previewSenderName: StateFlow<String?> = _previewSenderName.asStateFlow()

    val isReadOnlyPreview: StateFlow<Boolean> = _sharedPreviewShifts
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun startPreview(senderName: String, shifts: List<ShiftDay>) {
        _sharedPreviewShifts.value = shifts
        _previewSenderName.value = senderName
    }

    fun endPreview() {
        _sharedPreviewShifts.value = null
        _previewSenderName.value = null
    }

    // Main calendar states
    private val _selectedMonth = MutableStateFlow<YearMonth>(safeYearMonthNow())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(safeLocalDateNow())
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    // Database source streams
    val allShiftDays: StateFlow<List<ShiftDay>> = repository.allShiftDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combines database shifts with memory-based preview shifts if present
    val visibleShiftDays: StateFlow<List<ShiftDay>> = combine(allShiftDays, _sharedPreviewShifts) { localShifts, previewShifts ->
        previewShifts ?: localShifts
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state for editing active inputs
    val currentDayShift: Flow<ShiftDay?> = combine(visibleShiftDays, _selectedDate) { days, date ->
        if (date == null) null else days.find { it.date == date.toString() }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _selectedMonth.value = YearMonth.of(date.year, date.month)
    }

    fun clearSelectedDate() {
        _selectedDate.value = null
    }

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
        // Auto-select first day of next month to keep selection clean
        _selectedDate.value = _selectedMonth.value.atDay(1)
    }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
        // Auto-select first day of previous month to keep selection clean
        _selectedDate.value = _selectedMonth.value.atDay(1)
    }

    var activeUserName: String = ""

    private fun syncToRoster(date: LocalDate, type: String, length: Int) {
        val userName = activeUserName
        if (userName.isBlank()) return
        val year = date.year
        val month = date.monthValue
        val monthIndex = when {
            year == 2025 && month == 12 -> 0
            year == 2026 && month in 1..12 -> month
            else -> return
        }
        val day = date.dayOfMonth
        val code = when (type) {
            "MORNING" -> "R"
            "MORNING_PR" -> "PR"
            "NIGHT" -> "N"
            "NIGHT_PN" -> "PN"
            "VACATION" -> "D"
            "SICK" -> "CH"
            "KZ" -> "KZ"
            "Par" -> "Par"
            "MEETING" -> "P"
            "TRAINING" -> "V"
            else -> null
        }
        val hoursStr = if (code != null) length.toString() else null

        // Temporarily clear callback to prevent update recursion loops
        val cb = RosterData.onCellUpdatedExternal
        RosterData.onCellUpdatedExternal = null
        RosterData.updateCellForMonth(monthIndex, userName, day, code, hoursStr)
        RosterData.saveCurrentState(monthIndex)
        RosterData.onCellUpdatedExternal = cb

        val context = RosterData.appContext
        if (context != null && userName.isNotBlank()) {
            val dayStr = "${date.dayOfMonth}.${date.monthValue}.${date.year}"
            val codeDesc = when (type) {
                "MORNING" -> "Ranná (R)"
                "MORNING_PR" -> "PCO ranná (PR)"
                "NIGHT" -> "Nočná (N)"
                "NIGHT_PN" -> "PCO nočná (PN)"
                "VACATION" -> "Dovolenka (D)"
                "SICK" -> "Choroba (CH)"
                "KZ" -> "Kĺzavé voľno (KZ)"
                "Par" -> "Paragraf (Par)"
                "MEETING" -> "Poverenie (P)"
                "TRAINING" -> "Vzdelávanie (V)"
                else -> "Voľno"
            }
            RosterData.triggerRosterNotification(
                context = context,
                title = "Úprava zmeny: $userName",
                message = "Príslušník $userName si upravil smenu dňa $dayStr ($codeDesc).",
                targetOfficer = null,
                sender = userName
            )
        }
    }

    fun setShiftType(date: LocalDate, type: String, length: Int = 8, syncToRosterEnabled: Boolean = true) {
        if (_sharedPreviewShifts.value != null) return
        if (syncToRosterEnabled) {
            syncToRoster(date, type, length)
        }
        viewModelScope.launch {
            try {
                val existing = repository.getShiftDayDirect(date.toString())
                val updated = existing?.copy(shiftType = type, shiftLength = length) 
                    ?: ShiftDay(date = date.toString(), shiftType = type, shiftLength = length)
                repository.insertShiftDay(updated)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setShiftLength(date: LocalDate, length: Int) {
        if (_sharedPreviewShifts.value != null) return
        viewModelScope.launch {
            try {
                val existing = repository.getShiftDayDirect(date.toString())
                val updated = existing?.copy(shiftLength = length)
                    ?: ShiftDay(date = date.toString(), shiftType = "NONE", shiftLength = length)
                repository.insertShiftDay(updated)
                syncToRoster(date, updated.shiftType, length)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setOvertimeHours(date: LocalDate, hours: Int) {
        if (_sharedPreviewShifts.value != null) return
        viewModelScope.launch {
            try {
                val existing = repository.getShiftDayDirect(date.toString())
                val updated = existing?.copy(overtimeHours = hours)
                    ?: ShiftDay(date = date.toString(), shiftType = "NONE", shiftLength = 8, overtimeHours = hours)
                repository.insertShiftDay(updated)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveNoteAndReminder(date: LocalDate, note: String?, reminder: String?) {
        if (_sharedPreviewShifts.value != null) return
        viewModelScope.launch {
            try {
                val existing = repository.getShiftDayDirect(date.toString())
                val updated = existing?.copy(note = note, reminderText = reminder)
                    ?: ShiftDay(
                        date = date.toString(),
                        shiftType = "NONE",
                        shiftLength = 8,
                        note = note,
                        reminderText = reminder
                    )
                repository.insertShiftDay(updated)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearDayData(date: LocalDate) {
        if (_sharedPreviewShifts.value != null) return
        syncToRoster(date, "NONE", 0)
        viewModelScope.launch {
            try {
                repository.deleteShiftDayByDate(date.toString())
                repository.deleteEventsForDate(date.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Quick templates - fill month days in rotation sequence
    fun applyTemplateForRemainingDays(shiftSequence: List<String>, length: Int = 8) {
        if (_sharedPreviewShifts.value != null) return
        val month = _selectedMonth.value
        val daysInMonth = month.lengthOfMonth()
        val currentShifts = allShiftDays.value
        val selDate = _selectedDate.value

        viewModelScope.launch {
            try {
                var anchorDayNum: Int? = null
                var anchorShiftType: String? = null

                // First check if selected date in current month has a matching shift
                if (selDate != null && selDate.year == month.year && selDate.monthValue == month.monthValue) {
                    val existingSel = currentShifts.find { it.date == selDate.toString() }
                    if (existingSel != null && existingSel.shiftType != "NONE" && existingSel.shiftType in shiftSequence) {
                        anchorDayNum = selDate.dayOfMonth
                        anchorShiftType = existingSel.shiftType
                    }
                }

                // Otherwise find the first existing shift in current month matching sequence
                if (anchorShiftType == null) {
                    for (dayNum in 1..daysInMonth) {
                        val dateStr = month.atDay(dayNum).toString()
                        val existing = currentShifts.find { it.date == dateStr }
                        if (existing != null && existing.shiftType != "NONE" && existing.shiftType in shiftSequence) {
                            anchorDayNum = dayNum
                            anchorShiftType = existing.shiftType
                            break
                        }
                    }
                }

                val finalAnchorDayNum = anchorDayNum ?: 1
                val finalAnchorShiftType = anchorShiftType ?: shiftSequence.firstOrNull { it != "NONE" } ?: "NIGHT"
                val anchorIndex = shiftSequence.indexOf(finalAnchorShiftType).let { if (it == -1) 0 else it }
                val seqSize = shiftSequence.size

                val toInsert = mutableListOf<ShiftDay>()
                for (dayNum in 1..daysInMonth) {
                    val dateObj = month.atDay(dayNum)
                    val dateStr = dateObj.toString()

                    val daysFromAnchor = dayNum - finalAnchorDayNum
                    val seqIdx = (((anchorIndex + daysFromAnchor) % seqSize) + seqSize) % seqSize
                    val type = shiftSequence[seqIdx]

                    val existing = currentShifts.find { it.date == dateStr }
                    val updated = existing?.copy(shiftType = type, shiftLength = length)
                        ?: ShiftDay(date = dateStr, shiftType = type, shiftLength = length)
                    toInsert.add(updated)
                    syncToRoster(dateObj, type, length)
                }

                if (toInsert.isNotEmpty()) {
                    repository.insertShiftDays(toInsert)
                }

                com.example.ui.RosterData.saveCurrentState()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Clear all shifts for current month
    fun clearCurrentMonthData() {
        if (_sharedPreviewShifts.value != null) return
        val month = _selectedMonth.value
        val daysInMonth = month.lengthOfMonth()
        val currentShifts = allShiftDays.value

        viewModelScope.launch {
            try {
                val toInsert = mutableListOf<ShiftDay>()
                for (dayNum in 1..daysInMonth) {
                    val dateObj = month.atDay(dayNum)
                    val dateStr = dateObj.toString()
                    val existing = currentShifts.find { it.date == dateStr }
                    val updated = existing?.copy(
                        shiftType = "NONE",
                        shiftLength = 8,
                        note = null,
                        reminderText = null,
                        overtimeHours = 0
                    ) ?: ShiftDay(
                        date = dateStr,
                        shiftType = "NONE",
                        shiftLength = 8,
                        note = null,
                        reminderText = null,
                        overtimeHours = 0
                    )
                    toInsert.add(updated)
                    syncToRoster(dateObj, "NONE", 8)
                }

                if (toInsert.isNotEmpty()) {
                    repository.insertShiftDays(toInsert)
                }

                com.example.ui.RosterData.saveCurrentState()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Helper functions for shift sharing (compact Base64 encoding)
    fun generateShareCode(senderName: String, shifts: List<ShiftDay>, onlyCycle: Boolean = false): String {
        val activeShifts = shifts.filter { 
            if (onlyCycle) {
                it.shiftType != "NONE"
            } else {
                it.shiftType != "NONE" || !it.note.isNullOrEmpty() || !it.reminderText.isNullOrEmpty() || it.overtimeHours > 0 
            }
        }
        val sb = java.lang.StringBuilder()
        sb.append("V1|").append(senderName).append("|")
        activeShifts.forEachIndexed { index, shift ->
            if (index > 0) sb.append(";")
            sb.append(shift.date).append(",")
            sb.append(shift.shiftType).append(",")
            sb.append(shift.shiftLength).append(",")
            val overtime = if (onlyCycle) 0 else shift.overtimeHours
            sb.append(overtime).append(",")
            val noteStr = if (onlyCycle) "" else (shift.note ?: "")
            sb.append(java.net.URLEncoder.encode(noteStr, "UTF-8")).append(",")
            val reminderStr = if (onlyCycle) "" else (shift.reminderText ?: "")
            sb.append(java.net.URLEncoder.encode(reminderStr, "UTF-8"))
        }
        val bytes = sb.toString().toByteArray(Charsets.UTF_8)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
    }

    fun importShareCode(code: String): Pair<String, List<ShiftDay>>? {
        if (code.isBlank()) return null
        return try {
            val trimmed = code.trim()
            val decodedBytes = android.util.Base64.decode(trimmed, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
            val decodedString = String(decodedBytes, Charsets.UTF_8)
            if (!decodedString.startsWith("V1|")) return null
            val parts = decodedString.substring(3).split("|", limit = 2)
            if (parts.size < 2) return null
            val senderName = parts[0]
            val shiftsString = parts[1]
            if (shiftsString.isEmpty()) {
                return Pair(senderName, emptyList())
            }
            val shiftList = shiftsString.split(";").map { dayStr ->
                val fields = dayStr.split(",")
                val date = fields[0]
                val shiftType = fields[1]
                val shiftLength = fields[2].toIntOrNull() ?: 8
                val overtimeHours = fields[3].toIntOrNull() ?: 0
                val note = java.net.URLDecoder.decode(fields[4], "UTF-8").let { if (it.isEmpty()) null else it }
                val reminderText = java.net.URLDecoder.decode(fields[5], "UTF-8").let { if (it.isEmpty()) null else it }
                ShiftDay(
                    date = date,
                    shiftType = shiftType,
                    shiftLength = shiftLength,
                    note = note,
                    reminderText = reminderText,
                    overtimeHours = overtimeHours
                )
            }
            Pair(senderName, shiftList)
        } catch (e: Exception) {
            null
        }
    }
}

class ShiftViewModelFactory(private val repository: ShiftRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShiftViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShiftViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
