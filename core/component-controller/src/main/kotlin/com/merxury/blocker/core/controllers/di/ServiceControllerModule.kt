/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.core.controllers.di

import com.merxury.blocker.core.controllers.IServiceController
import com.merxury.blocker.core.controllers.root.RootServiceController
import com.merxury.blocker.core.controllers.shizuku.ShizukuServiceController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface ServiceControllerModule {
    @Binds
    @RootServiceControl
    fun bindsRootServiceController(rootServiceController: RootServiceController): IServiceController

    @Binds
    @ShizukuServiceControl
    fun bindsShizukuServiceController(shizukuServiceController: ShizukuServiceController): IServiceController
}
