// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject

/** Supplies the paths and streams of an unpacked, read-only catalog snapshot. */
internal interface CatalogSource {
    fun paths(): Set<String>
    fun open(path: String): InputStream
}

internal class BundledCatalogSource @Inject constructor(
    @ApplicationContext context: Context,
) : CatalogSource {
    private val assets = context.assets

    override fun paths(): Set<String> = buildSet {
        fun collectFiles(path: String) {
            val children = assets.list("catalog/$path").orEmpty()
            if (children.isEmpty()) add(path) else children.forEach { collectFiles("$path/$it") }
        }

        assets.list("catalog").orEmpty().forEach(::collectFiles)
    }

    override fun open(path: String): InputStream = assets.open("catalog/$path")
}
