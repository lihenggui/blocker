package com.merxury.blocker.core.ui.data

import com.merxury.blocker.core.ui.AppDetailTabs

data class SelectedApp(
    val packageName: String = "",
    val tab: AppDetailTabs = AppDetailTabs.Info,
    val searchKeyword: List<String> = listOf(),
)
