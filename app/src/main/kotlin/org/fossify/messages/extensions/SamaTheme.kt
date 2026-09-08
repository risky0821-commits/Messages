package org.fossify.messages.extensions

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import org.fossify.commons.R as CommonsR

const val SAMA_THEME_LIGHT = 0
const val SAMA_THEME_DARK = 1

fun Context.getSamaThemeMode(): Int {
    val background = config.backgroundColor
    val luminance = (
        0.299 * Color.red(background) +
            0.587 * Color.green(background) +
            0.114 * Color.blue(background)
        ) / 255.0

    return if (luminance < 0.5) SAMA_THEME_DARK else SAMA_THEME_LIGHT
}

fun Context.applySamaTheme(mode: Int) {
    val isDark = mode == SAMA_THEME_DARK
    val textColor = ContextCompat.getColor(
        this,
        if (isDark) CommonsR.color.theme_dark_text_color else CommonsR.color.theme_light_text_color
    )
    val backgroundColor = ContextCompat.getColor(
        this,
        if (isDark) CommonsR.color.theme_dark_background_color else CommonsR.color.theme_light_background_color
    )
    val primaryColor = ContextCompat.getColor(this, CommonsR.color.color_primary)

    config.apply {
        isSystemThemeEnabled = false
        isGlobalThemeEnabled = false

        this.textColor = textColor
        this.backgroundColor = backgroundColor
        this.primaryColor = primaryColor
        accentColor = primaryColor

        customTextColor = textColor
        customBackgroundColor = backgroundColor
        customPrimaryColor = primaryColor
        customAccentColor = primaryColor
        customAppIconColor = primaryColor

        // Sama no longer exposes launcher/color customization. Keeping the default icon state
        // also avoids the old alias switch that used the applicationId as a class package.
        appIconColor = primaryColor
        isUsingModifiedAppIcon = false
    }
}
