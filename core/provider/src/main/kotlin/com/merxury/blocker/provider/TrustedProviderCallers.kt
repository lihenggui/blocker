// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.provider

/**
 * Apps other than Blocker that may call [ComponentProvider], mapped to the SHA-256
 * digests (lowercase hex) of the signing certificates accepted for each package.
 *
 * LibChecker is signed by its own key, so a `signature` permission would lock it out.
 */
internal val TRUSTED_PROVIDER_CALLERS: Map<String, Set<String>> = mapOf(
    "com.absinthe.libchecker" to setOf(
        // Google Play, GitHub Releases and IzzyOnDroid (developer key, CN=Bozhen Zhao).
        // The Google Play build was checked on an installed copy and carries this key.
        "b12e1cfbfe285849d6740018022eec2209feb28c2685df6f4d2f97dbde3b5775",
        // F-Droid (F-Droid's per-app key, CN=FDroid)
        "47274114ab10ade16e41088a991be3ea4a621855f2487f1edc62187367f4a5c0",
    ),
)
