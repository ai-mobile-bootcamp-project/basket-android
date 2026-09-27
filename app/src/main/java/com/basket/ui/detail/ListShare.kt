package com.basket.ui.detail

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import com.basket.R
import com.basket.domain.ShareLabels

/** The localised pieces of the share text ("To buy (7)", "In basket (3)", "Total $45.84 (1 item without price)"). */
class ResourceShareLabels(private val resources: Resources) : ShareLabels {
    override fun toBuy(count: Int): String = resources.getString(R.string.share_to_buy, count)

    override fun inBasket(count: Int): String = resources.getString(R.string.share_in_basket, count)

    override fun total(formattedTotal: String, withoutPrice: Int): String =
        if (withoutPrice > 0) {
            resources.getQuantityString(R.plurals.share_total_without_price, withoutPrice, withoutPrice, formattedTotal)
        } else {
            resources.getString(R.string.share_total, formattedTotal)
        }
}

/** Opens the Android share sheet with plain text. */
fun Context.sharePlainText(text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, getString(R.string.share_chooser_title)))
}
