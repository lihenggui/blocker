/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.data.test

import com.merxury.blocker.core.data.appstate.IAppStateCache
import com.merxury.blocker.core.data.di.DataModule
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.respository.licenses.LicensesRepository
import com.merxury.blocker.core.data.respository.userdata.AppPropertiesRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.data.test.repository.FakeAppPropertiesRepository
import com.merxury.blocker.core.data.test.repository.FakeAppRepository
import com.merxury.blocker.core.data.test.repository.FakeComponentDetailRepository
import com.merxury.blocker.core.data.test.repository.FakeComponentRepository
import com.merxury.blocker.core.data.test.repository.FakeGeneralRuleRepository
import com.merxury.blocker.core.data.test.repository.FakeLicensesRepository
import com.merxury.blocker.core.data.test.repository.FakeUserDataRepository
import com.merxury.blocker.core.data.util.NetworkMonitor
import com.merxury.blocker.core.data.util.PermissionMonitor
import com.merxury.blocker.core.data.util.TimeZoneMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DataModule::class],
)
internal interface TestDataModule {
    @Binds
    fun bindUserDataRepository(
        userDataRepository: FakeUserDataRepository,
    ): UserDataRepository

    @Binds
    fun bindAppPropertiesRepository(
        appPropertiesRepository: FakeAppPropertiesRepository,
    ): AppPropertiesRepository

    @Binds
    fun bindsTestGeneralRuleRepository(
        testGeneralRuleRepository: FakeGeneralRuleRepository,
    ): GeneralRuleRepository

    @Binds
    fun bindsComponentDetailRepository(
        componentDetailRepository: FakeComponentDetailRepository,
    ): ComponentDetailRepository

    @Binds
    fun bindsComponentRepository(
        componentRepository: FakeComponentRepository,
    ): ComponentRepository

    @Binds
    fun bindsNetworkMonitor(
        networkMonitor: AlwaysOnlineNetworkMonitor,
    ): NetworkMonitor

    @Binds
    fun bindsPermissionMonitor(
        permissionMonitor: AlwaysGrantedPermissionMonitor,
    ): PermissionMonitor

    @Binds
    fun bindsAppRepository(
        appRepository: FakeAppRepository,
    ): AppRepository

    @Binds
    fun bindsAppStateCache(
        appStateCache: TestAppStateCache,
    ): IAppStateCache

    @Binds
    fun bindsTimeZoneMonitor(impl: DefaultZoneIdTimeZoneMonitor): TimeZoneMonitor

    @Binds
    fun bindsLicensesRepository(
        licensesRepository: FakeLicensesRepository,
    ): LicensesRepository
}
