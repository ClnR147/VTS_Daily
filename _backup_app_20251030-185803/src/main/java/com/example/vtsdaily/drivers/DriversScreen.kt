package com.example.vtsdaily.drivers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File

// Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.HorizontalDivider

private val VtsBanner = Color(0xFF4CAF50)   // green
private val VtsBannerText = Color(0xFFFFF5E1)
private val RowStripe = Color(0xFFF7F5FA)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DriversScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var drivers by remember { mutableStateOf(DriverStore.load(context)) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Driver?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val filtered = remember(drivers, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) drivers else drivers.filter { d ->
            d.name.lowercase().contains(q) ||
                    d.van.lowercase().contains(q)  ||
                    "${d.make} ${d.model}".lowercase().contains(q)
        }
    }

    fun doImport() {
        val guesses = listOf(
            "/storage/emulated/0/PassengerSchedules/driver.xls",
            "C:/Users/kbigg/CrossDevice/KEITH's S25+ (1)/storage/PassengerSchedules/driver.xls"
        ).map(::File)

        val found = guesses.firstOrNull { it.exists() }
        if (found == null) {
            scope.launch { snackbarHostState.showSnackbar("driver.xls not found in default locations.") }
        } else {
            runCatching { DriverStore.importFromXls(found) }
                .onSuccess {
                    drivers = it
                    DriverStore.save(context, it)
                    scope.launch { snackbarHostState.showSnackbar("Imported ${it.size} drivers.") }
                }
                .onFailure { e ->
                    scope.launch { snackbarHostState.showSnackbar("Import failed: ${e.message}") }
                }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { doImport() },
                containerColor = VtsBanner,
                contentColor = VtsBannerText
            ) {
                Icon(
                    imageVector = Icons.Filled.Upload,
                    contentDescription = "Import XLS"
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // divider under the main app bar (match Schedule feel)
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),  // aligns with search bar edges
                thickness = 8.dp,
                color = VtsBanner
            )

            // Search
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Search (name, van, make/model)") }
            )

            // List (extra bottom padding so rows don't hide behind the FAB)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered) { d ->
                    DriverRow(
                        driver = d,
                        onClick = { selected = d },
                        onCall = { phone ->
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }

        if (selected != null) {
            DriverDetailsDialog(
                driver = selected!!,
                onDismiss = { selected = null },
                onCall = { phone ->
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    context.startActivity(intent)
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DriverRow(
    driver: Driver,
    onClick: () -> Unit,
    onCall: (String) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onCall(driver.phone) }
            )
    ) {
        Row(
            modifier = Modifier
                .background(RowStripe)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Van pill
            Box(
                modifier = Modifier
                    .background(VtsBanner, shape = MaterialTheme.shapes.large)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(driver.van, color = VtsBannerText, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    driver.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val sub = buildString {
                    if (driver.year != null) append("${driver.year} ")
                    append(driver.make)
                    if (driver.model.isNotBlank()) append(" ${driver.model}")
                }.trim()
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF56536A),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = driver.phone,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onCall(driver.phone) },
                maxLines = 1, overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun DriverDetailsDialog(
    driver: Driver,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(color = VtsBanner, shape = MaterialTheme.shapes.large)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(driver.van, color = VtsBannerText, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = buildString {
                        append("• ")
                        if (driver.year != null) append("${driver.year} ")
                        append(driver.make)
                        if (driver.model.isNotBlank()) append(" ${driver.model}")
                    }.trim(),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column {
                Text(driver.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Phone: ", fontWeight = FontWeight.SemiBold)
                    Text(
                        driver.phone,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onCall(driver.phone) }
                    )
                }
            }
        }
    )
}
