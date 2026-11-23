/*
 * Copyright 2026 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.core.system.di

import com.merxury.blocker.core.system.PlatformSystemPackageReader
import com.merxury.blocker.core.system.SystemPackageReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the reader. The [com.merxury.blocker.core.system.PlatformPackages] it reads through is
 * bound by the module that owns the privileged channel, which decides which users it reaches.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface SystemModule {
    @Binds
    fun bindsSystemPackageReader(reader: PlatformSystemPackageReader): SystemPackageReader
}
