package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppFontStyle
import com.example.data.AppTextScale

private fun singleFontFamily(fontResId: Int): FontFamily = FontFamily(
    Font(fontResId, FontWeight.Normal),
    Font(fontResId, FontWeight.Medium),
    Font(fontResId, FontWeight.SemiBold),
    Font(fontResId, FontWeight.Bold)
)

val DisplayFontFamily = singleFontFamily(R.font.nunito)
val BodyFontFamily = singleFontFamily(R.font.plus_jakarta_sans)
val PoppinsFontFamily = singleFontFamily(R.font.poppins)
val LoraFontFamily = singleFontFamily(R.font.lora)
val SpaceGroteskFontFamily = singleFontFamily(R.font.space_grotesk)
val MonoFontFamily = singleFontFamily(R.font.jetbrains_mono)

private val InterFontFamily = singleFontFamily(R.font.inter)
private val RobotoFontFamily = singleFontFamily(R.font.roboto)
private val MontserratFontFamily = singleFontFamily(R.font.montserrat)
private val RalewayFontFamily = singleFontFamily(R.font.raleway)
private val OswaldFontFamily = singleFontFamily(R.font.oswald)
private val PlayfairFontFamily = singleFontFamily(R.font.playfair_display)
private val MerriweatherFontFamily = singleFontFamily(R.font.merriweather)
private val PacificoFontFamily = singleFontFamily(R.font.pacifico)
private val DancingScriptFontFamily = singleFontFamily(R.font.dancing_script)
private val CaveatFontFamily = singleFontFamily(R.font.caveat)
private val BebasNeueFontFamily = singleFontFamily(R.font.bebas_neue)
private val ComfortaaFontFamily = singleFontFamily(R.font.comfortaa)
private val QuicksandFontFamily = singleFontFamily(R.font.quicksand)
private val FredokaFontFamily = singleFontFamily(R.font.fredoka)
private val DmSansFontFamily = singleFontFamily(R.font.dm_sans)
private val RubikFontFamily = singleFontFamily(R.font.rubik)
private val FiraCodeFontFamily = singleFontFamily(R.font.fira_code)
private val RighteousFontFamily = singleFontFamily(R.font.righteous)
private val CinzelFontFamily = singleFontFamily(R.font.cinzel)

fun fontFamilyForStyle(fontStyle: AppFontStyle): FontFamily = when (fontStyle) {
    AppFontStyle.CLASSIC -> BodyFontFamily
    AppFontStyle.ROUNDED -> DisplayFontFamily
    AppFontStyle.MODERN -> PoppinsFontFamily
    AppFontStyle.SERIF -> LoraFontFamily
    AppFontStyle.MONO -> MonoFontFamily
    AppFontStyle.SPACE_GROTESK -> SpaceGroteskFontFamily
    AppFontStyle.INTER -> InterFontFamily
    AppFontStyle.ROBOTO -> RobotoFontFamily
    AppFontStyle.MONTSERRAT -> MontserratFontFamily
    AppFontStyle.RALEWAY -> RalewayFontFamily
    AppFontStyle.OSWALD -> OswaldFontFamily
    AppFontStyle.PLAYFAIR -> PlayfairFontFamily
    AppFontStyle.MERRIWEATHER -> MerriweatherFontFamily
    AppFontStyle.PACIFICO -> PacificoFontFamily
    AppFontStyle.DANCING_SCRIPT -> DancingScriptFontFamily
    AppFontStyle.CAVEAT -> CaveatFontFamily
    AppFontStyle.BEBAS_NEUE -> BebasNeueFontFamily
    AppFontStyle.COMFORTAA -> ComfortaaFontFamily
    AppFontStyle.QUICKSAND -> QuicksandFontFamily
    AppFontStyle.FREDOKA -> FredokaFontFamily
    AppFontStyle.DM_SANS -> DmSansFontFamily
    AppFontStyle.RUBIK -> RubikFontFamily
    AppFontStyle.FIRA_CODE -> FiraCodeFontFamily
    AppFontStyle.RIGHTEOUS -> RighteousFontFamily
    AppFontStyle.CINZEL -> CinzelFontFamily
}

fun buildTypography(
    fontStyle: AppFontStyle = AppFontStyle.CLASSIC,
    textScale: AppTextScale = AppTextScale.MEDIUM,
    boldText: Boolean = false
): Typography {
    val bodyFamily = fontFamilyForStyle(fontStyle)
    val headingFamily = when (fontStyle) {
        AppFontStyle.CLASSIC -> DisplayFontFamily
        AppFontStyle.MODERN -> SpaceGroteskFontFamily
        else -> bodyFamily
    }
    val scale = textScale.multiplier
    val widthFix = when (fontStyle) {
        AppFontStyle.MONO, AppFontStyle.FIRA_CODE -> 0.94f
        AppFontStyle.CAVEAT -> 1.08f
        else -> 1.0f
    }
    val factor = scale * widthFix

    fun w(base: FontWeight): FontWeight = if (!boldText) base else when (base) {
        FontWeight.Normal -> FontWeight.Medium
        FontWeight.Medium -> FontWeight.SemiBold
        FontWeight.SemiBold -> FontWeight.Bold
        else -> FontWeight.ExtraBold
    }

    return Typography(
        displaySmall = TextStyle(
            fontFamily = headingFamily,
            fontWeight = w(FontWeight.Bold),
            fontSize = (30f * factor).sp,
            lineHeight = (36f * factor).sp,
            letterSpacing = (-0.25).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = headingFamily,
            fontWeight = w(FontWeight.Bold),
            fontSize = (25f * factor).sp,
            lineHeight = (31f * factor).sp,
            letterSpacing = (-0.15).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = headingFamily,
            fontWeight = w(FontWeight.SemiBold),
            fontSize = (22f * factor).sp,
            lineHeight = (28f * factor).sp
        ),
        titleLarge = TextStyle(
            fontFamily = headingFamily,
            fontWeight = w(FontWeight.Bold),
            fontSize = (21f * factor).sp,
            lineHeight = (26f * factor).sp
        ),
        titleMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = w(FontWeight.SemiBold),
            fontSize = (16f * factor).sp,
            lineHeight = (22f * factor).sp
        ),
        titleSmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = w(FontWeight.SemiBold),
            fontSize = (14f * factor).sp,
            lineHeight = (20f * factor).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = w(FontWeight.Normal),
            fontSize = (15f * factor).sp,
            lineHeight = (22f * factor).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = w(FontWeight.Normal),
            fontSize = (14f * factor).sp,
            lineHeight = (20f * factor).sp
        ),
        bodySmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = w(FontWeight.Normal),
            fontSize = (12.5f * factor).sp,
            lineHeight = (17f * factor).sp
        ),
        labelLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = w(FontWeight.SemiBold),
            fontSize = (14f * factor).sp,
            lineHeight = (20f * factor).sp
        ),
        labelMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = w(FontWeight.Medium),
            fontSize = (12f * factor).sp,
            lineHeight = (16f * factor).sp
        ),
        labelSmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = w(FontWeight.Medium),
            fontSize = (11.5f * factor).sp,
            lineHeight = (15f * factor).sp
        )
    )
}

val CipherTypography = buildTypography()
