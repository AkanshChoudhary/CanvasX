package com.my_app.art_collab.ui.theme

import androidx.compose.ui.graphics.Color

// ── Primary: Indigo-Violet ──────────────────────────────────────────────────
val Indigo10  = Color(0xFF0F0846)
val Indigo20  = Color(0xFF1C1265)
val Indigo30  = Color(0xFF2B1E8C)
val Indigo40  = Color(0xFF4833D4)   // light-mode primary — richer, more vivid
val Indigo60  = Color(0xFF7C6CF5)   // dark-mode primary
val Indigo80  = Color(0xFFB0A4FF)   // dark-mode onPrimaryContainer
val Indigo90  = Color(0xFFE0D8FF)   // light-mode primaryContainer — more saturated
val Indigo95  = Color(0xFFEEE8FF)   // light-mode surfaceContainerHigh

// ── Secondary: Rose-Coral ───────────────────────────────────────────────────
val Rose10  = Color(0xFF3E001D)
val Rose20  = Color(0xFF5C1130)
val Rose30  = Color(0xFF7D2748)
val Rose40  = Color(0xFFBE1555)     // light-mode secondary
val Rose60  = Color(0xFFFF8FAB)     // dark-mode secondary
val Rose80  = Color(0xFFFFADC8)
val Rose90  = Color(0xFFFFD9E2)

// ── Tertiary: Teal-Mint ──────────────────────────────────────────────────────
val Teal10  = Color(0xFF001F1D)
val Teal20  = Color(0xFF003733)
val Teal40  = Color(0xFF006B65)     // light-mode tertiary
val Teal60  = Color(0xFF4DD0C8)     // dark-mode tertiary
val Teal80  = Color(0xFF70EDE5)
val Teal90  = Color(0xFF9EF2EC)

// ── Neutral surfaces – dark ─────────────────────────────────────────────────
val DarkBackground  = Color(0xFF0A0A12)
val DarkSurface     = Color(0xFF13131E)
val DarkSurface2    = Color(0xFF1A1A28)
val DarkSurface3    = Color(0xFF222235)

// ── Neutral surfaces – light (pure whites + color-tinted containers) ─────────
val LightBackground      = Color(0xFFFFFBFF)   // near-pure white — strong contrast with containers
val LightSurface         = Color(0xFFFFFFFF)   // pure white
val LightSurfaceVariant  = Color(0xFFEDE8FF)   // pronounced lavender — visually distinct
val LightSurfaceContainer     = Color(0xFFF4F0FF)  // soft lavender for cards/sheets
val LightSurfaceContainerHigh = Color(0xFFEEE8FF)  // slightly stronger for elevated items

// ── On-colors ────────────────────────────────────────────────────────────────
val OnDark   = Color(0xFFE6E0F8)
val OnLight  = Color(0xFF1A1830)    // deep indigo-black — richer than pure black

// ── Neutrals for outline / variant surfaces ──────────────────────────────────
val Outline              = Color(0xFF4A4565)
val OutlineVariant       = Color(0xFF2D2948)
val OutlineLight         = Color(0xFF6E6888)   // stronger outline for light mode
val OutlineVariantLight  = Color(0xFFBEB6D8)   // more visible dividers
