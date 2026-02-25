package com.example.vtsdaily.ui.templates

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.vtsdaily.ui.components.ScreenDividers
import com.example.vtsdaily.ui.components.VtsCard
import com.example.vtsdaily.ui.components.VtsSearchBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SortOption<T>(
    val label: String,
    val comparator: Comparator<T>,
    val primaryText: (T) -> String,
    val secondaryText: (T) -> String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectoryHeader(
    showTopDivider: Boolean,
    searchLabel: String,
    query: String,
    onQueryChange: (String) -> Unit,
    sortOptions: List<String>,
    sortIndex: Int,
    onSortIndexChange: (Int) -> Unit,
    // ✅ ONE knob for “search bar distance from thick divider”
    afterDividerGap: Dp = 4.dp,
    horizontalPadding: Dp = 12.dp
) {
    if (showTopDivider) {
        ScreenDividers.Thick()
        Spacer(Modifier.height(afterDividerGap))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
    ) {
        VtsSearchBar(
            value = query,
            onValueChange = onQueryChange,
            label = searchLabel
        )

        if (sortOptions.size > 1) {
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                sortOptions.forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = sortIndex == idx,
                        onClick = { onSortIndexChange(idx) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = sortOptions.size)
                    ) { Text(label) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DirectoryTemplateScreen(
    items: List<T>,
    sortOptions: List<SortOption<T>>,
    itemKey: (T) -> String,

    // Search
    searchLabel: String,
    searchHintPredicate: (T, String) -> Boolean,

    // Row fields
    phoneOf: (T) -> String,
    // Optional override. If null, we will ACTION_DIAL by default.
    onCallPhone: ((String) -> Unit)? = null,

    // Trailing actions
    onEdit: (T) -> Unit,

    // Two-phase delete hooks (Option A)
    onDeleteImmediate: (T) -> Unit,
    onDeleteFinal: (T) -> Unit,
    onUndo: (T) -> Unit,

    // UX strings
    deleteSnackbarMessage: (T) -> String = { "Deleted" },
    deleteSnackbarActionLabel: String = "Undo",

    // UI tweaks
    // ✅ default TRUE so Contacts/Clinics share the same divider rhythm
    showTopDivider: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
    rowSpacing: Dp = 10.dp,
    deleteAnimationMs: Long = 180L,

    // ✅ expose the “move search bar up/down” knob if you ever need it per-screen
    afterDividerGap: Dp = 4.dp
) {
    require(sortOptions.isNotEmpty()) { "sortOptions must not be empty" }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ✅ Guaranteed call handler (works even if onCallPhone is null)
    val callPhone: (String) -> Unit = remember(context, onCallPhone) {
        { raw ->
            val phone = raw.trim()
            if (phone.isBlank()) return@remember

            if (onCallPhone != null) {
                onCallPhone(phone)
            } else {
                val intent = Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    var query by rememberSaveable { mutableStateOf("") }
    var sortIndex by rememberSaveable { mutableIntStateOf(0) }

    val listState = rememberLazyListState()

    LaunchedEffect(sortIndex, query) {
        if (listState.layoutInfo.totalItemsCount > 0) {
            listState.scrollToItem(0)
        }
    }

    val selectedSort = remember(sortOptions, sortIndex) {
        sortOptions.getOrElse(sortIndex) { sortOptions.first() }
    }

    val sorted = remember(items, selectedSort) {
        items.sortedWith(selectedSort.comparator)
    }

    val filtered = remember(sorted, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) sorted else sorted.filter { item -> searchHintPredicate(item, q) }
    }

    // Tracks rows currently animating out
    val deletingKeys = remember { mutableStateOf(setOf<String>()) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            DirectoryHeader(
                showTopDivider = showTopDivider,
                searchLabel = searchLabel,
                query = query,
                onQueryChange = { query = it },
                sortOptions = sortOptions.map { it.label },
                sortIndex = sortIndex,
                onSortIndexChange = { sortIndex = it },
                afterDividerGap = afterDividerGap,
                horizontalPadding = 12.dp
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(rowSpacing)
            ) {
                itemsIndexed(
                    filtered,
                    key = { _, item -> itemKey(item) }
                ) { _, item ->
                    val key = itemKey(item)
                    val isDeleting = deletingKeys.value.contains(key)

                    AnimatedVisibility(
                        visible = !isDeleting,
                        enter = fadeIn(tween(deleteAnimationMs.toInt())) +
                                expandVertically(tween(deleteAnimationMs.toInt())),
                        exit = fadeOut(tween(deleteAnimationMs.toInt())) +
                                shrinkVertically(tween(deleteAnimationMs.toInt()))
                    ) {
                        DirectoryRowCard(
                            primaryText = selectedSort.primaryText(item),
                            secondaryText = selectedSort.secondaryText(item),
                            phone = phoneOf(item),
                            onCall = callPhone,
                            onEdit = { onEdit(item) },
                            onDelete = {
                                scope.launch {
                                    // 1) animate out
                                    deletingKeys.value = deletingKeys.value + key
                                    delay(deleteAnimationMs)

                                    // 2) immediate removal (UI state)
                                    onDeleteImmediate(item)

                                    // allow key reuse if item reappears
                                    deletingKeys.value = deletingKeys.value - key

                                    // 3) undo window
                                    val result = snackbar.showSnackbar(
                                        message = deleteSnackbarMessage(item),
                                        actionLabel = deleteSnackbarActionLabel,
                                        duration = SnackbarDuration.Long
                                    )

                                    // 4) finalize vs undo
                                    if (result == SnackbarResult.ActionPerformed) {
                                        onUndo(item)
                                    } else {
                                        onDeleteFinal(item)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun DirectoryRowCard(
    primaryText: String,
    secondaryText: String,
    phone: String,
    onCall: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    VtsCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {

                if (primaryText.isNotBlank()) {
                    Text(
                        primaryText,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (secondaryText.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (phone.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        phone,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onCall(phone) },
                        maxLines = 1
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
        }
    }
}