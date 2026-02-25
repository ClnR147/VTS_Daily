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

object VtsSearchSpec {
    val HorizontalPad = 12.dp
    val VerticalPad = 6.dp
    val HeightMin = 56.dp
    val Corner = 18.dp
}

@Composable
fun VtsSearchBarCanonical(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    // This is the *only* place that decides shape/height/padding.
    VtsSearchBar(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VtsSearchSpec.HorizontalPad, vertical = VtsSearchSpec.VerticalPad)
            .heightIn(min = VtsSearchSpec.HeightMin)
    )
}

@Composable
fun VtsSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        // keep your shape/colors/icons exactly as you already have them
    )
}