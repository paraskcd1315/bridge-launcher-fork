package com.tored.bridgelauncher.api2.server.endpoints

import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.annotation.RequiresApi
import com.tored.bridgelauncher.api2.server.CalendarEventSerializable
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.services.calendar.CalendarEvents
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Date

class CalendarEventsEndpoint(private val _calendarEvents: CalendarEvents) : IBridgeServerEndpoint {
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse {
        val queryParam = req.url.getQueryParameter("q") ?: ""
        val fromParam = req.url.getQueryParameter("from")
        val toParam = req.url.getQueryParameter("to")
        val (from, to) = if (fromParam != null && toParam != null) {
            val fromDate = Date(Instant.parse(fromParam).toEpochMilli())
            val toDate = Date(Instant.parse(toParam).toEpochMilli())
            buildCalendarDateRange(fromDate, toDate)
        } else {
            Pair(parseIsoToMillis(fromParam), parseIsoToMillis(toParam))
        }
        val page = req.url.getQueryParameter("page")?.toIntOrNull()
        val limit = req.url.getQueryParameter("limit")?.toIntOrNull()

        val results = _calendarEvents.queryEvents(queryParam, from, to, page, limit)
        val json = Json.encodeToString(ListSerializer(CalendarEventSerializable.serializer()), results)

        return WebResourceResponse(
            "application/json",
            "utf-8",
            json.byteInputStream()
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseIsoToMillis(value: String?): Long? {
        return try {
            value?.let { Instant.parse(it).toEpochMilli() }
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun buildCalendarDateRange(fromDate: java.util.Date, toDate: java.util.Date): Pair<Long, Long> {
        val from = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            time = fromDate
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val to = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            time = toDate
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }.timeInMillis

        return Pair(from, to)
    }
}