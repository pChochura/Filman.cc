package com.pointlessapps.filman.data.api

import org.jsoup.Jsoup

internal fun isCloudflareChallengePage(html: String): Boolean {
    val document = Jsoup.parse(html)
    return document.title().startsWith("Just a moment", ignoreCase = true) ||
        document.selectFirst("#cf-browser-verification, #challenge-form, #challenge-stage, #cf-challenge-running") != null ||
        (html.contains("challenge-platform", ignoreCase = true) &&
            html.contains("_cf_chl_opt", ignoreCase = true))
}
