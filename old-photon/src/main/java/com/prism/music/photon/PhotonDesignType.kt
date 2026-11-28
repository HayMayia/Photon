package com.prism.music.photon

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.prism.music.R

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal)
)

// Photon disciplined 3-size Inter typographic scale:
// Title: 20sp SemiBold (600), 28sp line height, -0.01em tracking
// Body / BodyMedium: 14sp Regular (400) / Medium (500), 20sp line height
// Caption: 12sp Regular (400), 16sp line height, 0.01em tracking
val PhotonTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontFeatureSettings = "tnum",
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.01).em
)

val PhotonBodyStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontFeatureSettings = "tnum",
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.em
)

val PhotonBodyMediumStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontFeatureSettings = "tnum",
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.em
)

val PhotonCaptionStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontFeatureSettings = "tnum",
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.01.em
)

val PhotonTypography = Typography(
    displayLarge = PhotonTitleStyle,
    displayMedium = PhotonTitleStyle,
    displaySmall = PhotonTitleStyle,
    headlineLarge = PhotonTitleStyle,
    headlineMedium = PhotonTitleStyle,
    headlineSmall = PhotonTitleStyle,
    titleLarge = PhotonTitleStyle,
    titleMedium = PhotonTitleStyle,
    titleSmall = PhotonBodyMediumStyle,
    bodyLarge = PhotonBodyStyle,
    bodyMedium = PhotonBodyStyle,
    bodySmall = PhotonCaptionStyle,
    labelLarge = PhotonBodyMediumStyle,
    labelMedium = PhotonCaptionStyle,
    labelSmall = PhotonCaptionStyle
)

/** A separate ten-foot scale; phone's 12–20sp scale is not readable across a room. */
internal val PhotonTvTypography = PhotonTypography.copy(
    headlineLarge = PhotonTitleStyle.copy(fontSize = 36.sp, lineHeight = 44.sp),
    headlineMedium = PhotonTitleStyle.copy(fontSize = 32.sp, lineHeight = 40.sp),
    headlineSmall = PhotonTitleStyle.copy(fontSize = 32.sp, lineHeight = 40.sp),
    titleLarge = PhotonTitleStyle.copy(fontSize = 24.sp, lineHeight = 32.sp),
    titleMedium = PhotonTitleStyle.copy(fontSize = 22.sp, lineHeight = 30.sp),
    titleSmall = PhotonBodyMediumStyle.copy(fontSize = 22.sp, lineHeight = 30.sp),
    bodyLarge = PhotonBodyStyle.copy(fontSize = 22.sp, lineHeight = 30.sp),
    bodyMedium = PhotonBodyStyle.copy(fontSize = 22.sp, lineHeight = 30.sp),
    labelLarge = PhotonBodyMediumStyle.copy(fontSize = 22.sp, lineHeight = 30.sp),
    labelMedium = PhotonCaptionStyle.copy(fontSize = 20.sp, lineHeight = 28.sp)
)
