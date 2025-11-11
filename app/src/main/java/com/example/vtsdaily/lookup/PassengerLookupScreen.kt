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
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.example.vtsdaily.ui.theme.VtsGreen
import androidx.compose.foundation.lazy.LazyListState


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerLookupScreen(
    // Optional; safe to leave connected or not
    registerActions: ((onLookupByDate: () -> Unit, onImport: () -> Unit) -> Unit)? = null,
    registerSetQuery: (((String) -> Unit) -> Unit)? = null // NEW
)  {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var page by rememberSaveable { mutableStateOf(Page.NAMES) }
    var query by rememberSaveable { mutableStateOf("") }
    var allRows by remember { mutableStateOf(LookupStore.load(context)) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }

    // ✅ save & restore scroll position for each page
    val namesListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState(0, 0) }
    val detailsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState(0, 0) }

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

    // ✅ plain try/catch version
    fun doImport() {
        try {
            val guesses: List<File> = listOf(
                "/storage/emulated/0/PassengerSchedules/CustomerLookup.csv"
            ).map(::File)

            val found: File = guesses.firstOrNull { it.exists() }
                ?: throw IllegalStateException("Lookup .csv not found in default locations.")

            logImportStart(found) // ensure visible if in another file
            val canonFile: File = canonicalizeCsvHeaderToTemp(found, context)
            debugImportRejectionsFromFile(canonFile)

            val imported: List<LookupRow> = importLookupCsv(canonFile)
            val merged = LookupStore.mergeAndSave(context, allRows, imported)
            allRows = merged
            page = Page.NAMES

            scope.launch { snackbar.showSnackbar("Imported ${imported.size} new rows (merged).") }
        } catch (e: Throwable) {
            scope.launch { snackbar.showSnackbar("Import failed: ${e.message ?: "Unknown error"}") }
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

    // NEW: expose a setter so MainActivity (or the Schedule screen) can prefill the query
    LaunchedEffect(registerSetQuery) {
        registerSetQuery?.invoke { name ->
            query = name
            page = Page.NAMES
            selectedName = null
            scope.launch { namesListState.scrollToItem(0) } // snap to top for a clean view
        }
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
                        onSelect = { selectedName = it; page = Page.DETAILS },
                        // 👉 add this param in NamesPage: listState: LazyListState? = null
                        listState = namesListState
                    )
                    Page.DETAILS -> DetailsPage(
                        name = selectedName.orEmpty(),
                        allRows = allRows,
                        // 👉 add this param in DetailsPage: listState: LazyListState? = null
                        listState = detailsListState
                    )
                }
            }

            if (page == Page.DETAILS) {
                SmallFloatingActionButton(
                    onClick = { page = Page.NAMES },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                    containerColor = VtsGreen
                ) {
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
