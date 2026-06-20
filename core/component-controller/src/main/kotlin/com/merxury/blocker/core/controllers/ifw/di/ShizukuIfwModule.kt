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

package com.merxury.blocker.core.controllers.ifw.di

import com.merxury.blocker.core.controllers.ifw.shizuku.RealShizukuPrivilegeProvider
import com.merxury.blocker.core.controllers.ifw.shizuku.RootOrShizukuIfwAccessChecker
import com.merxury.blocker.core.controllers.ifw.shizuku.RoutingIfwFileSystem
import com.merxury.blocker.core.controllers.ifw.shizuku.ShizukuIfwFileSystem
import com.merxury.blocker.core.controllers.ifw.shizuku.ShizukuPrivilegeProvider
import com.merxury.core.ifw.IfwAccessChecker
import com.merxury.core.ifw.IfwFileSystem
import com.merxury.core.ifw.ShizukuIfwFs
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ShizukuIfwModule {

    @Binds
    @Singleton
    fun bindsIfwFileSystem(impl: RoutingIfwFileSystem): IfwFileSystem

    @Binds
    @Singleton
    @ShizukuIfwFs
    fun bindsShizukuIfwFileSystem(impl: ShizukuIfwFileSystem): IfwFileSystem

    @Binds
    @Singleton
    fun bindsIfwAccessChecker(impl: RootOrShizukuIfwAccessChecker): IfwAccessChecker

    @Binds
    @Singleton
    fun bindsShizukuPrivilegeProvider(impl: RealShizukuPrivilegeProvider): ShizukuPrivilegeProvider
}
