package com.example.vtsdaily.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DirectoryHeader(
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

    Column(modifier = Modifier.fillMaxWidth()) {

        // ✅ Search field owns its own horizontal padding
        VtsDirectorySearchField(
            value = query,
            onValueChange = onQueryChange,
            label = searchLabel,
            horizontalPadding = horizontalPadding
        )

        if (sortOptions.size > 1) {
            Spacer(Modifier.height(6.dp))

            // ✅ Sort row owns its own horizontal padding (only once)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            ) {
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
}