package com.example.vtsdaily

import androidx.compose.runtime.Composable
import java.time.LocalDate
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

) {
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
                .background(Color(0xFF9A7DAB)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Time",
                modifier = Modifier.weight(1f),
                color = Color(0xFFFFF5E1),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Name",
                modifier = Modifier.weight(2f),
                color = Color(0xFFFFF5E1),
                fontWeight = FontWeight.Bold
            )
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