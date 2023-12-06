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

import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.ifw.IfwController
import com.merxury.blocker.core.controllers.root.api.RootApiController
import com.merxury.blocker.core.controllers.root.command.RootController
import com.merxury.blocker.core.controllers.shizuku.IShizukuInitializer
import com.merxury.blocker.core.controllers.shizuku.ShizukuController
import com.merxury.blocker.core.controllers.shizuku.ShizukuInitializer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface ControllerModule {
    @Binds
    @RootCommandControl
    fun bindsRootController(rootController: RootController): IController

    @Binds
    @IfwControl
    fun bindsIfwController(ifwController: IfwController): IController

    @Binds
    @ShizukuControl
    fun bindsShizukuController(shizukuController: ShizukuController): IController

    @Binds
    @RootApiControl
    fun bindsRootApiController(rootApiController: RootApiController): IController

    @Binds
    fun bindsShizukuInitializer(shizukuInitializer: ShizukuInitializer): IShizukuInitializer
}
