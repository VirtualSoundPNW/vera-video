package org.veraproject.veravideo.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.veraproject.veravideo.R
import org.veraproject.veravideo.util.formatDate
import java.time.Duration
import java.time.Instant

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Which bound the open date-picker dialog is editing. */
private enum class Bound { AFTER, BEFORE }

/**
 * Bottom sheet for the published-date window. Fully controlled: [after]/[before]
 * come from the query, and every edit is reported immediately through
 * [onRangeChange] so results and the filter chip update live. "Done" only closes.
 *
 * The two pickers constrain each other — the "before" picker can't select a day
 * earlier than "after", and vice versa — which is how the "second date must not
 * precede the first" rule is enforced (an out-of-order range is unpickable).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeSheet(
    after: Instant?,
    before: Instant?,
    onRangeChange: (after: Instant?, before: Instant?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var picking by remember { mutableStateOf<Bound?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.date_range_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.date_range_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DateField(
                label = stringResource(R.string.date_range_from),
                value = after,
                onClick = { picking = Bound.AFTER },
            )
            DateField(
                label = stringResource(R.string.date_range_to),
                value = before,
                onClick = { picking = Bound.BEFORE },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.filter_clear))
                }
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.date_range_done))
                }
            }
        }
    }

    when (picking) {
        Bound.AFTER -> BoundPickerDialog(
            headline = stringResource(R.string.date_range_pick_start),
            initial = after,
            // The start day can't be later than an already-chosen end day.
            selectableUpTo = before,
            onConfirm = { millis ->
                onRangeChange(millis?.let { startOfDay(it) }, before)
                picking = null
            },
            onDismiss = { picking = null },
        )

        Bound.BEFORE -> BoundPickerDialog(
            headline = stringResource(R.string.date_range_pick_end),
            initial = before,
            // The end day can't be earlier than an already-chosen start day.
            selectableFrom = after,
            onConfirm = { millis ->
                onRangeChange(after, millis?.let { endOfDay(it) })
                picking = null
            },
            onDismiss = { picking = null },
        )

        null -> Unit
    }
}

@Composable
private fun DateField(label: String, value: Instant?, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = value?.let { formatDate(it) } ?: stringResource(R.string.date_range_not_set),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoundPickerDialog(
    headline: String,
    initial: Instant?,
    selectableFrom: Instant? = null,
    selectableUpTo: Instant? = null,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val minDay = selectableFrom?.let { floorToUtcDay(it.toEpochMilli()) }
    val maxDay = selectableUpTo?.let { floorToUtcDay(it.toEpochMilli()) }
    val today = floorToUtcDay(System.currentTimeMillis())

    val state: DatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let { floorToUtcDay(it.toEpochMilli()) },
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                if (utcTimeMillis > today) return false // videos can't be published in the future
                if (minDay != null && utcTimeMillis < minDay) return false
                if (maxDay != null && utcTimeMillis > maxDay) return false
                return true
            }
        },
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.selectedDateMillis) }) {
                Text(stringResource(R.string.date_range_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        DatePicker(state = state, title = { Text(headline, modifier = Modifier.padding(24.dp)) })
    }
}

/** DatePicker returns UTC midnight of the chosen day; that already is the day's start. */
private fun startOfDay(utcMidnightMillis: Long): Instant = Instant.ofEpochMilli(utcMidnightMillis)

/** For the upper bound, include the whole chosen day: the last instant before the next. */
private fun endOfDay(utcMidnightMillis: Long): Instant =
    Instant.ofEpochMilli(utcMidnightMillis).plus(Duration.ofDays(1)).minusMillis(1)

/** Floor an arbitrary instant-in-millis to the UTC midnight the date picker speaks in. */
private fun floorToUtcDay(millis: Long): Long = millis - Math.floorMod(millis, DAY_MS)
