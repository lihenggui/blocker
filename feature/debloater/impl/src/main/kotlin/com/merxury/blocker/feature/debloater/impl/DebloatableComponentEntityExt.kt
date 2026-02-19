package com.merxury.blocker.feature.debloater.impl

import com.merxury.blocker.core.database.debloater.DebloatableComponentEntity

fun DebloatableComponentEntity.matchesClassifications(
    classifications: Set<ComponentClassification>,
    appPermissions: List<String> = emptyList(),
): Boolean {
    if (classifications.isEmpty()) {
        return true
    }

    return classifications.any { classification ->
        when (classification) {
            ComponentClassification.SHAREABLE -> isShareableComponent(this)
            ComponentClassification.DEEPLINK -> isDeeplinkEntry(this)
            ComponentClassification.LAUNCHER -> isLauncherEntry(this)
            ComponentClassification.WAKELOCK -> isWakelockComponent(this, appPermissions)
            ComponentClassification.AUTO_START -> isAutoStartReceiver(this)
            ComponentClassification.EXPORTED_NO_PERM -> isExportedNoPerm(this)
            ComponentClassification.FOREGROUND_SERVICE -> isForegroundService(this)
            ComponentClassification.PUSH_SERVICE -> isPushService(this)
            ComponentClassification.DANGEROUS_PROVIDER -> isDangerousProvider(this)
        }
    }
}
