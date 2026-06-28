package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String, // format "YYYY-MM-DD"
    val title: String,
    val description: String? = null
)
