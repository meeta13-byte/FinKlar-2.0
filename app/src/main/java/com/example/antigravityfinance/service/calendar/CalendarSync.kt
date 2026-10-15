package com.example.antigravityfinance.service.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Optional alert-to-calendar sync.
 *
 * Uses the CalendarContract provider rather than the Google Calendar API: the app's auth
 * is Firebase Phone Auth with no Google scopes, so the API would need an OAuth flow the
 * user never asked for. The provider writes into whichever calendar the device exposes as
 * writable, which is the calendar of the account the user has entered.
 *
 * Every method is a no-op when the permissions are not granted, so alerts keep working
 * unchanged when the feature is off or denied.
 */
object CalendarSync {
    private const val TAG = "CalendarSync"

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Calendars the app can write to. Returns empty when permission is missing. */
    fun writableCalendars(context: Context): List<Pair<Long, String>> {
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= " +
            "${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR} AND " +
            "${CalendarContract.Calendars.VISIBLE} != 0"
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.getLong(idIdx) to (cursor.getString(nameIdx) ?: "Calendar"))
                    }
                }
            } ?: emptyList()
        }.onFailure { Log.w(TAG, "Calendar query failed: ${it.javaClass.simpleName}") }.getOrDefault(emptyList())
    }

    /**
     * Creates the event, or updates it in place when [eventId] already exists. Returns the
     * event id, or null when there is no writable calendar or permission was denied.
     */
    fun upsertEvent(
        context: Context,
        eventId: Long?,
        title: String,
        startTimeMillis: Long,
        durationMillis: Long = 30 * 60 * 1000L,
        reminderMinutesBefore: Int? = null
    ): Long? {
        if (!hasPermission(context)) {
            Log.i(TAG, "SKIPPED sync: calendar permission not granted")
            return null
        }
        val calendarId = writableCalendars(context).firstOrNull()?.first ?: run {
            Log.i(TAG, "SKIPPED sync: no writable calendar on this device")
            return null
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startTimeMillis)
            put(CalendarContract.Events.DTEND, startTimeMillis + durationMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, if (reminderMinutesBefore != null) 1 else 0)
        }

        return runCatching {
            val resultId: Long? = if (eventId != null) {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                context.contentResolver.update(uri, values, null, null)
                eventId
            } else {
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    ?.lastPathSegment?.toLongOrNull()
            }
            resultId?.also { id ->
                if (reminderMinutesBefore != null) setReminder(context, id, reminderMinutesBefore)
                Log.i(TAG, "Synced alert to calendar event $id")
            }
        }.onFailure { Log.w(TAG, "Event write failed: ${it.javaClass.simpleName}") }.getOrNull()
    }

    private fun setReminder(context: Context, eventId: Long, minutesBefore: Int) {
        val reminder = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutesBefore)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        runCatching { context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder) }
            .onFailure { Log.w(TAG, "Reminder write failed: ${it.javaClass.simpleName}") }
    }

    /** Removes a previously synced event. Safe to call with a null or stale id. */
    fun deleteEvent(context: Context, eventId: Long?) {
        if (eventId == null || !hasPermission(context)) return
        runCatching {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(uri, null, null)
            Log.i(TAG, "Deleted calendar event $eventId")
        }.onFailure { Log.w(TAG, "Event delete failed: ${it.javaClass.simpleName}") }
    }
}