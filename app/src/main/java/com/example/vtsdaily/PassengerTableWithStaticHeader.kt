package com.example.vtsdaily

import androidx.compose.runtime.Composable
import java.time.LocalDate
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vtsdaily.ui.theme.ActionGreen
import com.example.vtsdaily.ui.theme.OnPrimaryText
import com.example.vtsdaily.ui.theme.PrimaryGreen
import com.example.vtsdaily.ui.theme.PrimaryPurple

@Composable
fun PassengerTableWithStaticHeader(
    passengers: List<Passenger>,
    insertedPassengers: List<Passenger>,
    setInsertedPassengers: (List<Passenger>) -> Unit,
    scheduleDate: LocalDate,
    viewMode: TripViewMode,
    context: Context,
    onTripRemoved: (Passenger, TripRemovalReason) -> Unit,
    onTripReinstated: (Passenger) -> Unit

)

{
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = if (viewMode == TripViewMode.REMOVED) 0.dp else 8.dp)
    ) {
        // Static header row
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .background(PrimaryGreen),
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
                    .padding(start = 5.dp) // ← shift slightly to the right
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
                        .width(140.dp)       // <-- keep this width in the row too
                        .alignByBaseline()
                )
            }
        }


        Spacer(modifier = Modifier.height(if (viewMode == TripViewMode.REMOVED) 12.dp else 8.dp))



        PassengerTable(
            passengers = passengers,
            insertedPassengers = insertedPassengers,
            setInsertedPassengers = setInsertedPassengers,
            scheduleDate = scheduleDate,
            viewMode = viewMode,
            context = context,
            onTripRemoved = onTripRemoved,
            onTripReinstated = onTripReinstated
        )
    }
}