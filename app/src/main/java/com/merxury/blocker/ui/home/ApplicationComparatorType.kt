package com.merxury.blocker.ui.home

enum class ApplicationComparatorType(val value: Int) {
    ASCENDING_BY_LABEL(0),
    DESCENDING_BY_LABEL(1),
    INSTALLATION_TIME(2),
    LAST_UPDATE_TIME(3);

    companion object {
        fun from(findValue: Int): ApplicationComparatorType = ApplicationComparatorType.values().first { it.value == findValue }
    }
}