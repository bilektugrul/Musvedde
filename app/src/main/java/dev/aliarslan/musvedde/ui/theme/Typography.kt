package dev.aliarslan.musvedde.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.domain.EditorFont

@OptIn(ExperimentalTextApi::class)
private fun literata(weight: FontWeight) = Font(
    R.font.literata,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Literata = FontFamily(
    literata(FontWeight.Normal),
    literata(FontWeight.Medium),
    literata(FontWeight.SemiBold),
    literata(FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
private fun googleSans(weight: FontWeight) = Font(
    R.font.google_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val GoogleSans = FontFamily(
    googleSans(FontWeight.Normal),
    googleSans(FontWeight.Medium),
    googleSans(FontWeight.SemiBold),
    googleSans(FontWeight.Bold),
)

val MusveddeTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontFamily = Literata, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = Literata, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Literata),
    )
}

fun EditorFont.fontFamily(): FontFamily = when (this) {
    EditorFont.SERIF -> Literata
    EditorFont.SYSTEM -> FontFamily.Default
    EditorFont.GOOGLE_SANS -> GoogleSans
    EditorFont.MONO -> FontFamily.Monospace
}
