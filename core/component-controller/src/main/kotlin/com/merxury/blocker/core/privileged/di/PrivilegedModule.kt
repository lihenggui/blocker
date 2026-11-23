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

package com.merxury.blocker.core.privileged.di

import com.merxury.blocker.core.privileged.ActivePrivilegedChannel
import com.merxury.blocker.core.privileged.ChannelIfwFileSystem
import com.merxury.blocker.core.privileged.ChannelPlatformPackages
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.privileged.PrivilegedExecutor
import com.merxury.blocker.core.system.PlatformPackages
import com.merxury.core.ifw.IfwFileSystem
import com.merxury.core.ifw.PrivilegedIfwFileSystem
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface PrivilegedModule {
    @Binds
    fun bindsPrivilegedChannel(channel: ActivePrivilegedChannel): PrivilegedChannel

    @Binds
    fun bindsPrivilegedExecutor(channel: ActivePrivilegedChannel): PrivilegedExecutor

    @Binds
    fun bindsPlatformPackages(packages: ChannelPlatformPackages): PlatformPackages

    @Binds
    @PrivilegedIfwFileSystem
    fun bindsPrivilegedIfwFileSystem(fileSystem: ChannelIfwFileSystem): IfwFileSystem
}
