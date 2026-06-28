package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shift_days")
    fun getAllShiftDays(): Flow<List<ShiftDay>>

    @Query("SELECT * FROM shift_days WHERE date = :date")
    suspend fun getShiftDayDirect(date: String): ShiftDay?

    @Query("SELECT * FROM shift_days WHERE date = :date")
    fun getShiftDay(date: String): Flow<ShiftDay?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShiftDay(day: ShiftDay)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShiftDays(days: List<ShiftDay>)

    @Delete
    suspend fun deleteShiftDay(day: ShiftDay)

    @Query("SELECT * FROM events ORDER BY id ASC")
    fun getAllEvents(): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE date = :date ORDER BY id ASC")
    fun getEventsForDate(date: String): Flow<List<Event>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: Event)

    @Delete
    suspend fun deleteEvent(event: Event)
    
    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEventById(eventId: Int)

    @Query("DELETE FROM shift_days WHERE date = :date")
    suspend fun deleteShiftDayByDate(date: String)

    @Query("DELETE FROM events WHERE date = :date")
    suspend fun deleteEventsForDate(date: String)
}
