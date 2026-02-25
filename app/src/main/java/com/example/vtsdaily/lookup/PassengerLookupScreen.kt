package com.example.vtsdaily.lookup

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.vtsdaily.DateSelectActivity
import com.example.vtsdaily.sanitizeName
import com.example.vtsdaily.ui.components.DirectoryHeader
import com.example.vtsdaily.ui.components.VtsSearchBar
import com.example.vtsdaily.ui.components.VtsSearchBarCanonical
import com.example.vtsdaily.ui.theme.VtsGreen
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerLookupScreen(
    // Optional; safe to leave connected or not
    registerActions: ((onLookupByDate: () -> Unit, onPredictByDate: () -> Unit, onImport: () -> Unit) -> Unit)? = null,
    registerSetQuery: (((String) -> Unit) -> Unit)? = null // NEW
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var page by rememberSaveable { mutableStateOf(Page.NAMES) }
    var query by rememberSaveable { mutableStateOf("") }
    var allRows by remember { mutableStateOf(LookupStore.load(context)) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }

    // NEW: sort toggle index (0 = Name, 1 = Trips)
    var sortIndex by rememberSaveable { mutableIntStateOf(0) }

    // ✅ save & restore scroll position for each page
    val namesListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState(0, 0) }
    val detailsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState(0, 0) }

    // ✅ Reposition Names list to the top when toggling Name <-> Trips (only on NAMES page)
    LaunchedEffect(sortIndex, page) {
        if (page == Page.NAMES) {
            namesListState.scrollToItem(0)
        }
    }

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

    // filtered + sorted names (NOW respects sortIndex)
    val nameList = remember(allRows, query, sortIndex, counts) {
        val q = query.trim()

        val base = allRows.asSequence()
            .map { it.passenger.orEmpty() }
            .filter { it.contains(q, ignoreCase = true) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        when (sortIndex) {
            1 -> base
                .sortedWith(
                    compareByDescending<String> { tripCountFor(it) }
                        .thenBy { it.lowercase() }
                )
                .toList()

            else -> base
                .sortedBy { it.lowercase() }
                .toList()
        }
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
            // Lookup by Date → existing history mode
            {
                val i = Intent(context, DateSelectActivity::class.java)
                context.startActivity(i)
            },
            // Predict by Date → same activity, but in prediction mode
            {
                val i = Intent(context, DateSelectActivity::class.java).apply {
                    putExtra("mode", "predict")   // DateSelectActivity can branch on this
                }
                context.startActivity(i)
            },
            // Import
            { doImport() }
        )
    }

    // NEW: expose a setter so MainActivity (or the Schedule screen) can prefill the query
    LaunchedEffect(registerSetQuery) {
        registerSetQuery?.invoke { pushedName ->
            val safe = sanitizeName(pushedName)
            if (safe != null) {
                query = safe
                selectedName = safe
                page = Page.DETAILS
            } else {
                query = pushedName
                selectedName = null
                page = Page.NAMES
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize()) {

                if (page == Page.NAMES) {
                    DirectoryHeader(
                        showTopDivider = true,
                        searchLabel = "Search name",
                        query = query,
                        onQueryChange = { query = it },

                        sortOptions = listOf("Name", "Trips"),
                        sortIndex = sortIndex,
                        onSortIndexChange = { sortIndex = it },
                        horizontalPadding = 12.dp
                    )}else {
            }

                when (page) {
                    Page.NAMES -> NamesPage(
                        nameList = nameList,
                        tripCountFor = ::tripCountFor,
                        onSelect = { selectedName = it; page = Page.DETAILS },
                        listState = namesListState
                    )

                    Page.DETAILS -> {
                        val safeName = selectedName.orEmpty()
                        DetailsPage(
                            name = safeName,
                            allRows = allRows,
                            tripCount = tripCountFor(safeName),
                            listState = detailsListState
                        )
                    }
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