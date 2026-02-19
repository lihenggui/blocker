package com.merxury.blocker.feature.debloater.impl

enum class ComponentClassification {
    SHAREABLE,
    DEEPLINK,
    LAUNCHER,
    WAKELOCK,
    AUTO_START,
    EXPORTED_NO_PERM,
    FOREGROUND_SERVICE,
    PUSH_SERVICE,
    DANGEROUS_PROVIDER,
}
