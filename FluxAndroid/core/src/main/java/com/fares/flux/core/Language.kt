package com.fares.flux.core

/** French on a device set to French, English everywhere else. */
val isFrench: Boolean get() = java.util.Locale.getDefault().language == "fr"

/** The text in the device's language: French as written, English otherwise. */
fun tr(french: String, english: String): String = if (isFrench) french else english
