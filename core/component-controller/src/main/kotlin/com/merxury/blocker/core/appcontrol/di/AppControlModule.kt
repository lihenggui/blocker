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

package com.merxury.blocker.core.appcontrol.di

import com.merxury.blocker.core.appcontrol.AppControl
import com.merxury.blocker.core.appcontrol.AppRestrictionControl
import com.merxury.blocker.core.appcontrol.BackgroundUsageControl
import com.merxury.blocker.core.appcontrol.PrivilegedAppControl
import com.merxury.blocker.core.appcontrol.PrivilegedAppRestrictionControl
import com.merxury.blocker.core.appcontrol.PrivilegedBackgroundUsageControl
import com.merxury.blocker.core.appcontrol.PrivilegedServiceControl
import com.merxury.blocker.core.appcontrol.ServiceControl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface AppControlModule {
    @Binds
    fun bindsAppControl(control: PrivilegedAppControl): AppControl

    @Binds
    fun bindsAppRestrictionControl(control: PrivilegedAppRestrictionControl): AppRestrictionControl

    @Binds
    fun bindsBackgroundUsageControl(control: PrivilegedBackgroundUsageControl): BackgroundUsageControl

    @Binds
    fun bindsServiceControl(control: PrivilegedServiceControl): ServiceControl
}
