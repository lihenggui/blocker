package com.merxury.blocker.feature.debloater

import com.merxury.blocker.core.database.debloater.DebloatableComponentEntity

fun DebloatableComponentEntity.matchesClassifications(
    classifications: Set<ComponentClassification>,
): Boolean {
    if (classifications.isEmpty()) {
        return true
    }

    return classifications.any { classification ->
        when (classification) {
            ComponentClassification.SHAREABLE -> isShareableComponent(this)
            ComponentClassification.DEEPLINK -> isDeeplinkEntry(this)
            ComponentClassification.LAUNCHER -> isLauncherEntry(this)
            ComponentClassification.EXPLICIT -> isExplicitLaunch(this)
        }
    }
}
