package com.safewalk.auth

import android.app.DatePickerDialog
import android.content.Context
import com.safewalk.R
import java.util.Calendar
import java.util.Locale

fun showBirthDatePicker(context: Context, initial: String? = null, onSelected: (String) -> Unit) {
    val selected = initial?.let { BirthDates.parse(it) } ?: Calendar.getInstance(Locale.ROOT).apply { set(2000, 0, 1) }
    DatePickerDialog(context, { _, year, month, day ->
        onSelected(String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day))
    }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).apply {
        setTitle(R.string.route_birth_date_title)
        datePicker.minDate = Calendar.getInstance(Locale.ROOT).apply { clear(); set(1900, 0, 1) }.timeInMillis
        datePicker.maxDate = System.currentTimeMillis()
    }.show()
}
