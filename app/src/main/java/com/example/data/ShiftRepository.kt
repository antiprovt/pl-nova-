package com.example.data

import kotlinx.coroutines.flow.Flow

class ShiftRepository(private val shiftDao: ShiftDao) {
    val allShiftDays: Flow<List<ShiftDay>> = shiftDao.getAllShiftDays()
    val allEvents: Flow<List<Event>> = shiftDao.getAllEvents()

    fun getShiftDay(date: String): Flow<ShiftDay?> = shiftDao.getShiftDay(date)
    suspend fun getShiftDayDirect(date: String): ShiftDay? = shiftDao.getShiftDayDirect(date)

    fun getEventsForDate(date: String): Flow<List<Event>> = shiftDao.getEventsForDate(date)

    suspend fun insertShiftDay(day: ShiftDay) = shiftDao.insertShiftDay(day)
    suspend fun insertShiftDays(days: List<ShiftDay>) = shiftDao.insertShiftDays(days)
    suspend fun deleteShiftDay(day: ShiftDay) = shiftDao.deleteShiftDay(day)

    suspend fun insertEvent(event: Event) = shiftDao.insertEvent(event)
    suspend fun deleteEvent(event: Event) = shiftDao.deleteEvent(event)
    suspend fun deleteEventById(eventId: Int) = shiftDao.deleteEventById(eventId)

    suspend fun deleteShiftDayByDate(date: String) = shiftDao.deleteShiftDayByDate(date)
    suspend fun deleteEventsForDate(date: String) = shiftDao.deleteEventsForDate(date)
}
