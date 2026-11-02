package com.fitshare.data

import java.util.Locale

/** FitShare speaks German on German phones and English everywhere else. */
fun isGerman(): Boolean = Locale.getDefault().language == "de"

fun tr(de: String, en: String): String = if (isGerman()) de else en
