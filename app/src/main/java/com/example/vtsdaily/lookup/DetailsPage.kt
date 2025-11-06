package com.example.vtsdaily.lookup

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.Color
import com.example.vtsdaily.ui.components.ScreenDividers

@Composable
fun DetailsPage(
    name: String,
    allRows: List<LookupRow>
) {
    val context = LocalContext.current
    val RowStripe = Color(0xFFF7F5FA)

    val groupedTrips = remember(allRows, name) {
        val trips = allRows.filter { it.passenger.equals(name, ignoreCase = true) }
        groupTripsByDate(trips)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            ElevatedCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CardGutter, vertical = 6.dp)
            ) {
                Column(
                    Modifier.padding(CardInner),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header: passenger name + phone
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2)

                    val phone = groupedTrips.values
                        .flatten()
                        .firstNotNullOfOrNull { it.phone?.trim()?.takeIf { p -> p.isNotBlank() } }
                    if (!phone.isNullOrBlank()) {
                        Text(
                            "Phone: $phone",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                context.startActivity(i)
                            }
                        )
                    }

                    // Header → first detail divider
                    ScreenDividers.Thick()

                    // Grouped trips by date (newest → oldest)
                    groupedTrips.entries.forEachIndexed { index, (date, trips) ->
                        if (index > 0) ScreenDividers.Thin()

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(RowStripe)
                                .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 0.dp)
                        ) {
                            Text(
                                "Date: ${formatDate(date)}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(Modifier.height(4.dp))

                            val labelIndent = 70.dp
                            trips.forEachIndexed { i, r ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    val pickup = r.pAddress.orEmpty()
                                    if (pickup.isNotBlank()) {
                                        Text(
                                            text = "Pickup:",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.width(labelIndent)
                                        )
                                        Text(
                                            text = pickup,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    val drop = r.dAddress.orEmpty()
                                    if (drop.isNotBlank()) {
                                        Text(
                                            text = "Drop-off:",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.width(labelIndent)
                                        )
                                        Text(
                                            text = drop,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                if (i < trips.lastIndex) Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

