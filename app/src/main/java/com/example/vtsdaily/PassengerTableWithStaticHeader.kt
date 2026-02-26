package com.example.vtsdaily

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vtsdaily.ui.components.ScreenDividers
import com.example.vtsdaily.ui.theme.ActionGreen
import com.example.vtsdaily.ui.theme.OnPrimaryText
import com.example.vtsdaily.ui.theme.PrimaryPurple
import com.example.vtsdaily.ui.theme.VtsGreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.clickable

@Composable
fun PassengerTableWithStaticHeader(
    passengers: List<Passenger>,
    insertedPassengers: List<Passenger>,
    setInsertedPassengers: (List<Passenger>) -> Unit,
    scheduleDate: LocalDate,
    viewMode: TripViewMode,
    context: Context,
    onTripRemoved: (Passenger, TripRemovalReason) -> Unit,
    onTripReinstated: (Passenger) -> Unit,
    schedulePassengers: List<Passenger>?,
    phoneBook: Map<String, String>,
    onDialerLaunched: () -> Unit = {},
    onLookupForName: (String) -> Unit = {},
    metricTextOverride: String? = null,
    onToggleViewMode: () -> Unit = {},
    onPickDate: () -> Unit = {}
) {
    val formattedDate = remember(scheduleDate) {
        scheduleDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
    }

    val metricText = metricTextOverride ?: run {
        val count = passengers.size
        when (viewMode) {
            TripViewMode.ACTIVE -> "$count Trips"
            TripViewMode.COMPLETED -> "$count Completed"
            TripViewMode.REMOVED -> "$count Removed"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()

    ) {

        // ✅ Big schedule header (chip can toggle viewMode now)
        ScheduleHeaderBlock(
            formattedDate = formattedDate,
            viewMode = viewMode,
            metricText = metricText,
            onToggleViewMode = onToggleViewMode,
            onPickDate = onPickDate
        )

        // ✅ Static header row — banner width matches card width (8.dp gutters)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp) // gutters OUTSIDE the green
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VtsGreen)     // green only within gutters
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Time",
                    color = OnPrimaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .padding(start = 5.dp)
                        .width(120.dp)
                        .alignByBaseline()
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Name",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnPrimaryText,
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline()
                )

                if (viewMode == TripViewMode.COMPLETED || viewMode == TripViewMode.REMOVED) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Phone",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnPrimaryText,
                        modifier = Modifier
                            .width(140.dp)
                            .alignByBaseline()
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)   // ✅ this is a CHILD of the Column, so weight is valid
        ) {
            PassengerTable(
                passengers = passengers,
                insertedPassengers = insertedPassengers,
                setInsertedPassengers = setInsertedPassengers,
                scheduleDate = scheduleDate,
                viewMode = viewMode,
                context = context,
                onTripRemoved = onTripRemoved,
                onTripReinstated = onTripReinstated,
                onLookupForName = onLookupForName,
                onDialerLaunched = onDialerLaunched
            )
        }
    }
}

@Composable
private fun ScheduleHeaderBlock(
    formattedDate: String,
    viewMode: TripViewMode,
    metricText: String,
    onToggleViewMode: () -> Unit,
    onPickDate: () -> Unit // ✅ NEW
) {
    val errorColor = MaterialTheme.colorScheme.error

    val statusText = remember(viewMode) {
        when (viewMode) {
            TripViewMode.ACTIVE -> "ACTIVE"
            TripViewMode.COMPLETED -> "COMPLETED"
            TripViewMode.REMOVED -> "REMOVED"
        }
    }

    val chipColor = remember(viewMode, errorColor) {
        when (viewMode) {
            TripViewMode.ACTIVE -> ActionGreen
            TripViewMode.COMPLETED -> PrimaryPurple
            TripViewMode.REMOVED -> errorColor
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // ✅ Date is tappable to pick schedule date
        Text(
            text = formattedDate,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPickDate() }, // ✅ NEW
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.clickable { onToggleViewMode() },
                shape = RoundedCornerShape(50),
                color = chipColor,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(16.dp))

            Text(
                text = metricText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            )
        }
    }
}