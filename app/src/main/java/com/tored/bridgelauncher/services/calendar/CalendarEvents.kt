package com.tored.bridgelauncher.services.calendar

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.CalendarContract
import android.util.Log
import androidx.annotation.RequiresApi
import com.tored.bridgelauncher.api2.server.CalendarEventSerializable
import java.time.Instant
import java.util.Calendar

class CalendarEvents(private val context: Context) {
    @RequiresApi(Build.VERSION_CODES.O)
    fun queryEvents(query: String, from: Long?, to: Long?, page: Int?, limit: Int?): List<CalendarEventSerializable> {
        val results = mutableListOf<CalendarEventSerializable>()

        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE
        )

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        // Load all events, including recurring yearly events

        if (query.isNotBlank()) {
            selectionParts.add("${CalendarContract.Events.TITLE} LIKE ?")
            selectionArgs.add("%$query%")
        }

        val selection = if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null
        val selectionArgsArray = if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null

        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        val cursor: Cursor? = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgsArray,
            sortOrder
        )

        if (cursor == null) {
            Log.d("CalendarEvents", "⚠️ Cursor is NULL")
        } else if (cursor.count == 0) {
            Log.d("CalendarEvents", "⚠️ Cursor is EMPTY")
        } else {
            Log.d("CalendarEvents", "✅ Cursor has ${cursor.count} rows")
        }

        cursor?.use {
            while (it.moveToNext()) {
                val title = it.getString(0) ?: ""
                val startTime = it.getLong(1)
                val endTime = it.getLong(2)
                val calendarName = it.getString(3)
                val calendarId = it.getLong(4)
                val allDay = it.getInt(5) == 1
                val rrule = it.getString(6)
                val isRecurringYearly = rrule?.contains("FREQ=YEARLY") == true
                val adjustedStart: Long? = if (isRecurringYearly && from != null && to != null) {
                    val originalDate = Calendar.getInstance().apply { timeInMillis = startTime }
                    val fromCal = Calendar.getInstance().apply { timeInMillis = from }
                    val toCal = Calendar.getInstance().apply { timeInMillis = to }

                    (fromCal.get(Calendar.YEAR)..toCal.get(Calendar.YEAR)).firstNotNullOfOrNull { year ->
                        try {
                            val test = Calendar.getInstance().apply {
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, originalDate.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, originalDate.get(Calendar.DAY_OF_MONTH))
                                if (allDay) {
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                } else {
                                    set(Calendar.HOUR_OF_DAY, originalDate.get(Calendar.HOUR_OF_DAY))
                                    set(Calendar.MINUTE, originalDate.get(Calendar.MINUTE))
                                    set(Calendar.SECOND, originalDate.get(Calendar.SECOND))
                                    set(Calendar.MILLISECOND, originalDate.get(Calendar.MILLISECOND))
                                }
                            }
                            test.timeInMillis.takeIf { it in from..to }
                        } catch (_: Exception) {
                            null // Ignore invalid dates like Feb 29 on non-leap years
                        }
                    }
                } else startTime
                // Manual date filter
                if (adjustedStart == null || (from != null && to != null && adjustedStart !in from..to)) {
                    continue
                }
                val adjustedEnd = if (endTime == 0L) {
                    adjustedStart + if (allDay) 86400000 else 3600000 // 1 day or 1 hour
                } else endTime
                // Retrieve the actual event ID if possible, fallback to UUID if not available
                val rawEventId = it.getColumnIndex(CalendarContract.Events._ID).takeIf { idx -> idx >= 0 }?.let { idx -> it.getLong(idx) }
                val eventId = rawEventId?.toString() ?: java.util.UUID.randomUUID().toString()
                results.add(
                    CalendarEventSerializable(
                        id = eventId,
                        title = title,
                        startTime = Instant.ofEpochMilli(adjustedStart).toString(),
                        endTime = Instant.ofEpochMilli(adjustedEnd).toString(),
                        calendarName = calendarName,
                        calendarId = calendarId,
                        allDay = allDay
                    )
                )
            }
        }

        results.sortBy { Instant.parse(it.startTime).toEpochMilli() }

        val safeLimit = limit ?: 100
        val safePage = page ?: 0
        val offset = safePage * safeLimit
        return results.drop(offset).take(safeLimit)
    }
}