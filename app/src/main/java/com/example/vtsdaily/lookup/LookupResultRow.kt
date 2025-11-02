package com.example.vtsdaily.lookup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Minimal row UI for Passenger Lookup list items.
 * Expects LookupRow to have: driveDate, passenger, pAddress, dAddress, phone.
 */
@Composable
fun LookupResultRow(
    row: LookupRow,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        tonalElevation = 0.5.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { m -> if (onClick != null) m.clickable { onClick() } else m }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Top line: Name • Phone
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = row.passenger.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                row.phone?.trim()?.takeIf { it.isNotBlank() }?.let { phone ->
                    Text(
                        text = phone,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }

            // Pickup
            val pickup = row.pAddress.orEmpty()
            if (pickup.isNotBlank()) {
                Text(
                    text = pickup,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Drop-off
            val dest = row.dAddress.orEmpty()
            if (dest.isNotBlank()) {
                Text(
                    text = dest,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

        }
    }
}

