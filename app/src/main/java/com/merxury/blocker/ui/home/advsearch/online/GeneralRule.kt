package com.merxury.blocker.ui.home.advsearch.online

data class GeneralRule(
    var name: String? = null,
    var iconUrl: String? = null,
    var company: String? = null,
    var searchKeyword: List<String> = listOf(),
    var useRegexSearch: Boolean? = null,
    var description: String? = null,
    var safeToBlock: Boolean? = null,
    var sideEffect: String? = null,
    var contributors: List<String> = listOf()
)