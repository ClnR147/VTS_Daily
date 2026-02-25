package com.example.vtsdaily.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow


@Composable
fun VtsHeaderSearchOnly(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    Column(Modifier.fillMaxWidth()) {
        VtsSearchBarCanonical(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier
                .padding(horizontal = HeaderSpec.ScreenHorizontal, vertical = HeaderSpec.SearchVPad)
        )

        Spacer(Modifier.height(HeaderSpec.GapBeforeDivider))
        ScreenDividers.Thick()
    }
}

@Composable
fun VtsHeaderSearchAndSort(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    sortIndex: Int,
    onSortChange: (Int) -> Unit,
    sortLabels: List<String>
) {
    Column(Modifier.fillMaxWidth()) {
        VtsSearchBarCanonical(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier
                .padding(horizontal = HeaderSpec.ScreenHorizontal, vertical = HeaderSpec.SearchVPad)
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HeaderSpec.ScreenHorizontal, vertical = HeaderSpec.SortVPad)
        ) {
            sortLabels.forEachIndexed { idx, text ->
                SegmentedButton(
                    selected = sortIndex == idx,
                    onClick = { onSortChange(idx) },
                    shape = SegmentedButtonDefaults.itemShape(idx, sortLabels.size)
                ) { Text(text) }
            }
        }

        Spacer(Modifier.height(HeaderSpec.GapBeforeDivider))
        ScreenDividers.Thick()
    }
}