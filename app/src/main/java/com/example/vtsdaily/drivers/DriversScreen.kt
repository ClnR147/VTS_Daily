package com.example.vtsdaily.drivers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriversScreen() {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var drivers by remember { mutableStateOf(DriverStore.load(context)) }

    val filtered = remember(drivers, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) drivers
        else drivers.filter { it.name.lowercase().contains(q) || it.van.lowercase().contains(q) }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Drivers") }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search by name or van") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { d ->
                    DriverRow(
                        driver = d,
                        onCall = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${d.phone}"))
                            context.startActivity(intent)
                        },
                        onToggleActive = {
                            DriverStore.toggleActive(context, d.id)
                            drivers = DriverStore.load(context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverRow(
    driver: Driver,
    onCall: () -> Unit,
    onToggleActive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCall() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = onCall,
                label = { Text("Van ${driver.van}") }
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = driver.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${driver.year} ${driver.makeModel}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val status = if (driver.active) "Active" else "Inactive"
                Text(text = status, style = MaterialTheme.typography.labelMedium)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCall) { Text("Call") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onToggleActive) { Text(if (driver.active) "Deactivate" else "Activate") }
            }
        }
    }
}
