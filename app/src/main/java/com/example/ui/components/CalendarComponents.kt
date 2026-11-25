package com.example.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatCalendarDateArabic(millis: Long): String {
    val arLocale = Locale.forLanguageTag("ar-IQ")
    val dayFmt = SimpleDateFormat("EEEE", arLocale)
    val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    val date = Date(millis)
    return "${dayFmt.format(date)} ${dateFmt.format(date)}"
}

fun formatDateKey(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onDateSelected(pickerState.selectedDateMillis ?: initialMillis)
                }
            ) {
                Text("تأكيد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    ) {
        DatePicker(state = pickerState)
    }
}
