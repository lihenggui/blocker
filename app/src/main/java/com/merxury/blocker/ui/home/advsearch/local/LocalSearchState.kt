package com.merxury.blocker.ui.home.advsearch.local

import com.merxury.blocker.data.Event
import com.merxury.blocker.data.app.InstalledApp

sealed class LocalSearchState {
    object NotStarted : LocalSearchState()
    data class Loading(val app: InstalledApp?) : LocalSearchState()
    data class Error(val exception: Event<Throwable>) : LocalSearchState()
    data class Finished(val count: Int) : LocalSearchState()
    object Searching : LocalSearchState()
}
