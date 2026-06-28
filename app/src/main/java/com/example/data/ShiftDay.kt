package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shift_days")
data class ShiftDay(
    @PrimaryKey
    val date: String, // format "YYYY-MM-DD"
    val shiftType: String, // "NONE", "MORNING", "MORNING_PR", "NIGHT", "NIGHT_PN", "VACATION", "SICK"
    val shiftLength: Int, // 8 or 12 hours
    val note: String? = null,
    val reminderText: String? = null,
    val overtimeHours: Int = 0
)
