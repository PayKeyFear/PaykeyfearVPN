package com.paykeyfear.vpn.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.paykeyfear.vpn.R

val SpaceGroteskFamily = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

val AppTypography = Typography(
    displayLarge  = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Bold,    fontSize = 57.sp),
    displayMedium = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Bold,    fontSize = 45.sp),
    displaySmall  = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Bold,    fontSize = 36.sp),
    headlineLarge = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp),
    headlineMedium= TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge    = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium   = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Medium,  fontSize = 16.sp),
    titleSmall    = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Medium,  fontSize = 14.sp),
    bodyLarge     = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Normal,  fontSize = 16.sp),
    bodyMedium    = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Normal,  fontSize = 14.sp),
    bodySmall     = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Normal,  fontSize = 12.sp),
    labelLarge    = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Medium,  fontSize = 14.sp),
    labelMedium   = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Medium,  fontSize = 12.sp),
    labelSmall    = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Medium,  fontSize = 11.sp),
)
