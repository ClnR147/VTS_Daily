package com.example.vtsdaily.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------- Brand ----------------
val VtsGreen = Color(0xFF4CAF50)   // VTS green

// ---------------- Legacy / existing app colors (kept) ----------------
val AppBackground = Color(0xFFF4FBF7)
val PrimaryPurple = Color(0xFF4A148C)         // If unused, safe to remove later

val SurfaceWhite = Color.White                // Card and surface default
val OnPrimaryText = Color(0xFFFFF5E1)         // Light text on purple (legacy)
val OnSurfaceText = Color.Black               // Text on white background

// --- View Mode Status Colors ---
val ActiveColor = Color(0xFF33691E)           // ACTIVE trip status
val CompletedColor = Color(0xFF01579B)        // COMPLETED trip status
val RemovedColor = Color(0xFFEF6C00)          // REMOVED trip status

// --- UI Element Highlights ---
val CardHighlight = Color(0xFF1A73E8)         // Card or row outline/accent
val ActionGreen = Color(0xFF2E7D32)           // Marking / done button

// --- Soft / Neutral Colors ---
val SubtleGrey = Color(0xFFF5F5F5)            // Light card backgrounds
val FromGrey = Color(0xFF5F6368)              // For "from" address text, icons, etc.

// ---------------- Slate & Green palette (Option #2) ----------------

// Base slate & neutrals (light)
val VtsSlate      = Color(0xFF37474F)  // deep slate (icons/text on darker areas)
val VtsSurface    = Color(0xFFF9FAFB)  // card/panel surface (soft light gray)
val VtsBackground = Color(0xFFECEFF1)  // app background (cool gray)
val VtsOutline    = Color(0xFFCFD8DC)  // dividers, strokes

// Text (light)
val VtsTextPrimary   = Color(0xFF263238)
val VtsTextSecondary = Color(0xFF4B4B4B)

// ---- Names your Theme expects (aliases to the above so nothing else breaks) ----
val VtsSurface_Light       = VtsSurface
val VtsBackground_Light    = VtsBackground
val VtsOutline_Light       = VtsOutline
val VtsTextPrimary_Light   = VtsTextPrimary
val VtsTextSecondary_Light = VtsTextSecondary

// ---- If you referenced these elsewhere, keep them too (same values) ----
val VtsSlate_LightText     = VtsTextPrimary   // primary text (light)
val VtsSecondaryText_Light = VtsTextSecondary // secondary text (light)

// Slate & neutrals (dark)
val VtsSlate_DarkSurface = Color(0xFF263238)  // surfaces (dark)
val VtsBackground_Dark   = Color(0xFF1B1F22)  // app background (dark)
val VtsOutline_Dark      = Color(0xFF455A64)  // dividers/strokes (dark)
val VtsText_OnDark       = Color(0xFFECEFF1)  // text/icons on dark

// Name your Theme expects (alias)
val VtsSurface_Dark = VtsSlate_DarkSurface

// ---------------- Status ----------------
val VtsSuccess = Color(0xFF81C784)
val VtsWarning = Color(0xFFFBC02D)
val VtsError   = Color(0xFFE57373)
