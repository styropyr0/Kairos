package com.styropyr0.kairos

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal fun Calendar.toString(format: String): String = SimpleDateFormat(format, Locale.getDefault()).format(time)

internal fun String.toCalendar(format: String = "yyyy-MM-dd"): Calendar {
    val sdf = SimpleDateFormat(format, Locale.getDefault())
    val date = sdf.parse(this)!!
    return Calendar.getInstance().apply { time = date }
}