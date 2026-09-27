package com.basket.ui.common

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.basket.R
import com.basket.domain.Category
import com.basket.domain.CategoryKey
import com.basket.domain.Dates
import com.basket.domain.Money
import java.time.ZoneId
import java.util.Locale

/** The app language (per-app locale from Settings, or the system language). */
val Resources.appLocale: Locale get() = configuration.locales[0]

val Context.appLocale: Locale get() = resources.appLocale

/** Money in the app language: "$1.74" · "1,74 US$". */
fun Context.formatMoney(cents: Long): String = Money.format(cents, appLocale)

@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

/** Money in the app language: "$1.74" · "1,74 US$". */
@Composable
@ReadOnlyComposable
fun formatMoney(cents: Long): String = Money.format(cents, appLocale())

/** "25 Sep, 09:14" in the app language and the phone's time zone. */
fun Context.formatShortDateTime(epochMillis: Long): String =
    Dates.shortDateTime(epochMillis, ZoneId.systemDefault(), appLocale)

@get:StringRes
val CategoryKey.labelRes: Int
    get() = when (this) {
        CategoryKey.FRUIT_VEG -> R.string.category_fruit_veg
        CategoryKey.BAKERY -> R.string.category_bakery
        CategoryKey.DAIRY_EGGS -> R.string.category_dairy_eggs
        CategoryKey.MEAT_FISH -> R.string.category_meat_fish
        CategoryKey.PANTRY -> R.string.category_pantry
        CategoryKey.FROZEN -> R.string.category_frozen
        CategoryKey.DRINKS -> R.string.category_drinks
        CategoryKey.HOUSEHOLD_PETS -> R.string.category_household_pets
        CategoryKey.OTHER -> R.string.category_other
    }

/** The name to show: the user's name for the category, or the default name in the app language. */
fun Category.displayName(resources: Resources): String =
    name ?: key?.let { resources.getString(it.labelRes) } ?: ""

fun Category.displayName(context: Context): String = displayName(context.resources)

@Composable
fun Category.label(): String = name ?: key?.let { stringResource(it.labelRes) } ?: ""

/**
 * The aisle emoji of a default category (design handover, "Category emoji"). Rendered as text with the system emoji
 * font; decorative only, so callers hide it from TalkBack. Categories the user added have none.
 */
val CategoryKey.emoji: String
    get() = when (this) {
        CategoryKey.FRUIT_VEG -> "🍎"
        CategoryKey.BAKERY -> "🍞"
        CategoryKey.DAIRY_EGGS -> "🥛"
        CategoryKey.MEAT_FISH -> "🥩"
        CategoryKey.PANTRY -> "🫙"
        CategoryKey.FROZEN -> "🧊"
        CategoryKey.DRINKS -> "🧃"
        CategoryKey.HOUSEHOLD_PETS -> "🧻"
        CategoryKey.OTHER -> "🛒"
    }

/** Emoji of this category, or null for a category the user added. */
val Category.emoji: String? get() = key?.emoji
