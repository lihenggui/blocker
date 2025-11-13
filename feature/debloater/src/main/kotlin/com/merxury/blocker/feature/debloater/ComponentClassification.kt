package com.merxury.blocker.feature.debloater

enum class ComponentClassification {
    SHAREABLE,
    DEEPLINK,
    LAUNCHER,
    EXPLICIT,
    WAKELOCK,
    AUTO_START,
    EXPORTED_NO_PERM,
    FOREGROUND_SERVICE,
    SYSTEM_SERVICE,
    PUSH_SERVICE,
    DANGEROUS_PROVIDER,
    INIT_PROVIDER,
}
