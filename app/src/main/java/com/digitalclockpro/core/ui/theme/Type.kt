package com.digitalclockpro.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        letterSpacing = 1.sp
    ),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.8.sp)
)
