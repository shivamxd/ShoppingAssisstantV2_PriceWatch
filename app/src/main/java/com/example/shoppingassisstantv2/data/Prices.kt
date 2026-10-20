package com.example.shoppingassisstantv2.data

import java.text.NumberFormat
import java.util.Locale

private val PRICE_IN_TEXT = Regex("""\d[\d,]*(?:\.\d{1,2})?""")
private val TARGET_INPUT = Regex("""\d+(?:\.\d{1,2})?""")

/** Reads the first price-like number out of page text such as "₹1,29,999.00". */
fun parsePrice(text: String?): Double? =
    text?.let { PRICE_IN_TEXT.find(it)?.value }?.replace(",", "")?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it > 0.0 }

/** Strict parsing for the target price the user types. */
fun parseTargetPrice(text: String): Double? {
    val clean = text.trim().removePrefix("₹").replace(",", "").trim()
    if (!clean.matches(TARGET_INPUT)) return null
    return clean.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
}

fun Double.asPrice(): String {
    val format = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
        maximumFractionDigits = if (this@asPrice % 1.0 == 0.0) 0 else 2
        minimumFractionDigits = 0
    }
    return "₹${format.format(this)}"
}

/** Plain, editable form for the target price field. */
fun Double.toPriceInput(): String =
    if (this % 1.0 == 0.0) String.format(Locale.US, "%.0f", this)
    else String.format(Locale.US, "%.2f", this)
