package com.example.vtsdaily.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.example.vtsdaily.ui.theme.LocalCardDividerStyle
import com.example.vtsdaily.ui.theme.LocalScreenDividerStyle

// ---------- Base ----------
@Composable
private fun BaseDivider(
    thickness: Dp,
    inset: Dp,
    verticalSpacing: Dp,
    color: Color,
    modifier: Modifier = Modifier
) {
    HorizontalDivider(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = inset, end = inset, top = verticalSpacing),
        thickness = thickness,
        color = color
    )
}

// ---------- Card dividers ----------
object CardDividers {

    // 🔹 NEW: inset-aware overloads so you can align with inner content (e.g., labels)
    @Composable fun Thin(inset: Dp, modifier: Modifier = Modifier) = with(LocalCardDividerStyle.current) {
        BaseDivider(thin, inset, verticalSpace, color, modifier)
    }
}

// ---------- Screen dividers ----------
object ScreenDividers {
    // Themed defaults (use LocalScreenDividerStyle.current.horizontalInset)
    @Composable fun Thin(modifier: Modifier = Modifier) = with(LocalScreenDividerStyle.current) {
        BaseDivider(thin, horizontalInset, verticalSpace, color, modifier)
    }

    @Composable fun Thick(modifier: Modifier = Modifier) = with(LocalScreenDividerStyle.current) {
        BaseDivider(thick, horizontalInset, verticalSpace, color, modifier)
    }

    // Inset-aware overloads (match card/parent widths precisely)
    @Composable fun Thin(inset: Dp, modifier: Modifier = Modifier) = with(LocalScreenDividerStyle.current) {
        BaseDivider(thin, inset, verticalSpace, color, modifier)
    }

}
