package com.ft_hangouts

import android.content.Context
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

// Timestamp formatting shared by the chat list and the message bubbles.
object TimeFormat {

    // Chat-app style: today shows the time, this week the weekday, older the date.
    // Uses the system's own formatters, so it follows the device language and
    // 12h/24h setting automatically — important for the two-language requirement.
    fun forConversationList(context: Context, timestamp: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }

        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        val daysApart = if (sameYear) {
            now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
        } else {
            Int.MAX_VALUE
        }

        return when {
            sameDay -> android.text.format.DateFormat.getTimeFormat(context).format(Date(timestamp))
            daysApart in 1..6 -> java.text.SimpleDateFormat("EEE", localeOf(context))
                .format(Date(timestamp))
            else -> DateFormat.getDateInstance(DateFormat.SHORT, localeOf(context))
                .format(Date(timestamp))
        }
    }

    // Time only, for the timestamp under each chat bubble.
    fun forMessageBubble(context: Context, timestamp: Long): String =
        android.text.format.DateFormat.getTimeFormat(context).format(Date(timestamp))

    private fun localeOf(context: Context) =
        context.resources.configuration.locales[0]
}
