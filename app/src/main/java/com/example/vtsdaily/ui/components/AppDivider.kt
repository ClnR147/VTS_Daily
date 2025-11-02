package com.example.vtsdaily.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    @Composable fun Thin(modifier: Modifier = Modifier) = with(LocalCardDividerStyle.current) {
        BaseDivider(thin, horizontalInset, verticalSpace, color, modifier)
    }
    @Composable fun Medium(modifier: Modifier = Modifier) = with(LocalCardDividerStyle.current) {
        BaseDivider(medium, horizontalInset, verticalSpace, color, modifier)
    }
    @Composable fun Thick(modifier: Modifier = Modifier) = with(LocalCardDividerStyle.current) {
        BaseDivider(thick, horizontalInset, verticalSpace, color, modifier)
    }
    /** Full-bleed variant inside cards (rare): */
    @Composable fun FullBleed(modifier: Modifier = Modifier) = with(LocalCardDividerStyle.current) {
        BaseDivider(medium, 0.dp, verticalSpace, color, modifier)
    }
    /** Nice for “Header → Details”: thicker + a touch more spacing */
    @Composable fun Section(modifier: Modifier = Modifier) = with(LocalCardDividerStyle.current) {
        BaseDivider(thick, horizontalInset, verticalSpace + thin, color, modifier)
    }
}

// ---------- Screen dividers ----------
object ScreenDividers {
    @Composable fun Thin(modifier: Modifier = Modifier) = with(LocalScreenDividerStyle.current) {
        BaseDivider(thin, horizontalInset, verticalSpace, color, modifier)
    }
    @Composable fun Medium(modifier: Modifier = Modifier) = with(LocalScreenDividerStyle.current) {
        BaseDivider(medium, horizontalInset, verticalSpace, color, modifier)
    }
    @Composable fun Thick(modifier: Modifier = Modifier) = with(LocalScreenDividerStyle.current) {
        BaseDivider(thick, horizontalInset, verticalSpace, color, modifier)
    }
    /** Inset screen divider (use when content area has horizontal padding) */
    @Composable fun Inset(modifier: Modifier = Modifier, inset: Dp = 16.dp) =
        with(LocalScreenDividerStyle.current) {
            BaseDivider(medium, inset, verticalSpace, color, modifier)
        }

    /** Section break for major page sections */
    @Composable fun Section(modifier: Modifier = Modifier) = with(LocalScreenDividerStyle.current) {
        BaseDivider(thick, horizontalInset, verticalSpace + thin, color, modifier)
    }
}

