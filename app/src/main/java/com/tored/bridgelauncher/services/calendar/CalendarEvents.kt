package com.tored.bridgelauncher.services.calendar

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.CalendarContract
import android.util.Log
import androidx.annotation.RequiresApi
import com.tored.bridgelauncher.api2.server.CalendarEventSerializable
import java.time.Instant
import android.content.ContentUris
import java.time.format.DateTimeFormatter

class CalendarEvents(private val context: Context) {
    @RequiresApi(Build.VERSION_CODES.O)
    fun queryEvents(query: String, from: Long?, to: Long?, page: Int?, limit: Int?): List<CalendarEventSerializable> {
        val results = mutableListOf<CalendarEventSerializable>()

        val begin = from ?: 0L
        val end = to ?: Long.MAX_VALUE

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, begin)
        ContentUris.appendId(builder, end)
        val instancesUri = builder.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.ALL_DAY
        )

        val selection: String? = if (query.isNotBlank()) {
            "${CalendarContract.Instances.TITLE} LIKE ?"
        } else null
        val selectionArgsArray: Array<String>? = if (query.isNotBlank()) {
            arrayOf("%$query%")
        } else null

        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

        val cursor: Cursor? = context.contentResolver.query(
            instancesUri,
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
                val title = it.getString(3) ?: ""
                val startTime = it.getLong(1)
                val endTime = it.getLong(2)
                val calendarName = it.getString(4)
                val calendarId = it.getLong(5)
                val allDay = it.getInt(6) == 1
                val rawEventId = it.getLong(0).toString()
                val formatter = DateTimeFormatter.ISO_INSTANT
                results.add(
                    CalendarEventSerializable(
                        id = rawEventId,
                        title = title,
                        startTime = formatter.format(Instant.ofEpochMilli(startTime)),
                        endTime = formatter.format(Instant.ofEpochMilli(endTime)),
                        calendarName = calendarName,
                        calendarId = calendarId,
                        allDay = allDay
                    )
                )
            }
        }

        results.sortBy {
            Instant.parse(it.startTime).toEpochMilli()
        }

        Log.d("CalendarEvents", "📅 Final result count: ${results.size}")
        results.forEach { event ->
            Log.d("CalendarEvents", "📝 Event: ${event.title} | AllDay: ${event.allDay} | Start: ${event.startTime} | End: ${event.endTime}")
        }

        return results
    }
}