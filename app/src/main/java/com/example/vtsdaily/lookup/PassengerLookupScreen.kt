package com.example.vtsdaily.lookup

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.vtsdaily.DateSelectActivity
import com.example.vtsdaily.ui.components.ScreenDividers
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.example.vtsdaily.ui.theme.VtsGreen


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerLookupScreen(
    // Make it optional so you can call this screen with or without wiring actions.
    registerActions: ((onLookupByDate: () -> Unit, onImport: () -> Unit) -> Unit)? = null
)  {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var page by rememberSaveable { mutableStateOf(Page.NAMES) }
    var query by rememberSaveable { mutableStateOf("") }
    var allRows by remember { mutableStateOf(LookupStore.load(context)) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }

    // trip counts (normalized)
    val counts by remember(allRows) {
        mutableStateOf(
            allRows.asSequence()
                .mapNotNull { it.passenger?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .map { it.replace(Regex("\\s+"), " ").lowercase() }
                .groupingBy { it }
                .eachCount()
        )
    }
    fun tripCountFor(displayName: String): Int {
        val key = displayName.trim().replace(Regex("\\s+"), " ").lowercase()
        return counts[key] ?: 0
    }

    // filtered names
    val nameList = remember(allRows, query) {
        val q = query.trim()
        allRows.asSequence()
            .map { it.passenger.orEmpty() }
            .filter { it.contains(q, ignoreCase = true) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .toList()
    }

    fun doImport() {
        // Build explicit List<File> to avoid type inference issues
        val guesses: List<File> = listOf(
            "/storage/emulated/0/PassengerSchedules/CustomerLookup.csv"
        ).map { p -> File(p) }

        val found: File? = guesses.firstOrNull { it.exists() }
        if (found == null) {
            scope.launch { snackbar.showSnackbar("Lookup .csv not found in default locations.") }
            return
        }

        logImportStart(found) // ensure not 'private' if in another file
        val canonFile: File = canonicalizeCsvHeaderToTemp(found, context) // ensure not 'private'
        debugImportRejectionsFromFile(canonFile)

        runCatching<List<LookupRow>> { importLookupCsv(canonFile) }
            .onSuccess { imported ->
                val merged = LookupStore.mergeAndSave(context, allRows, imported)
                allRows = merged
                page = Page.NAMES
                scope.launch { snackbar.showSnackbar("Imported ${imported.size} new rows (merged).") }
            }
            .onFailure { e ->
                scope.launch { snackbar.showSnackbar("Import failed: ${e.message}") }
            }
    }

    LaunchedEffect(registerActions) {
        registerActions?.invoke(
            {
                val i = Intent(context, DateSelectActivity::class.java)
                context.startActivity(i)
            },
            { doImport() }
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {

                if (page == Page.NAMES) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        singleLine = true,
                        label = { Text("Search name") }
                    )
                } else {
                    Spacer(Modifier.height(6.dp))
                }

                when (page) {
                    Page.NAMES -> NamesPage(
                        nameList = nameList,
                        tripCountFor = ::tripCountFor,
                        onSelect = { selectedName = it; page = Page.DETAILS }
                    )
                    Page.DETAILS -> DetailsPage(
                        name = selectedName.orEmpty(),
                        allRows = allRows
                    )
                }
            }

            if (page == Page.DETAILS) {
                SmallFloatingActionButton(
                    onClick = { page = Page.NAMES },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                    containerColor = VtsGreen                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        }
    }
}


private enum class Page { NAMES, DETAILS }
