/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.data.di

import com.merxury.blocker.core.data.appstate.AppStateCache
import com.merxury.blocker.core.data.appstate.IAppStateCache
import com.merxury.blocker.core.data.licenses.fetcher.AndroidLicensesFetcherImpl
import com.merxury.blocker.core.data.licenses.fetcher.LicensesFetcher
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.app.LocalAppRepository
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.component.LocalComponentRepository
import com.merxury.blocker.core.data.respository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.data.respository.componentdetail.LocalComponentDetailRepository
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleDataSource
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.respository.generalrule.LocalGeneralRuleDataSource
import com.merxury.blocker.core.data.respository.generalrule.OfflineFirstGeneralRuleRepository
import com.merxury.blocker.core.data.respository.licenses.LicensesRepository
import com.merxury.blocker.core.data.respository.licenses.LocalLicensesRepository
import com.merxury.blocker.core.data.respository.userdata.AppPropertiesRepository
import com.merxury.blocker.core.data.respository.userdata.LocalAppPropertiesRepository
import com.merxury.blocker.core.data.respository.userdata.LocalUserDataRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.data.util.AppPermissionMonitor
import com.merxury.blocker.core.data.util.ConnectivityManagerNetworkMonitor
import com.merxury.blocker.core.data.util.NetworkMonitor
import com.merxury.blocker.core.data.util.PermissionMonitor
import com.merxury.blocker.core.data.util.TimeZoneBroadcastMonitor
import com.merxury.blocker.core.data.util.TimeZoneMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    internal abstract fun bindUserDataRepository(
        userDataRepository: LocalUserDataRepository,
    ): UserDataRepository

    @Binds
    internal abstract fun bindAppPropertiesRepository(
        appPropertiesRepository: LocalAppPropertiesRepository,
    ): AppPropertiesRepository

    @Binds
    internal abstract fun bindsGeneralRuleRepository(
        generalRuleRepository: OfflineFirstGeneralRuleRepository,
    ): GeneralRuleRepository

    @Binds
    internal abstract fun bindsComponentDetailRepository(
        componentDetailRepository: LocalComponentDetailRepository,
    ): ComponentDetailRepository

    @Binds
    internal abstract fun bindsNetworkMonitor(
        networkMonitor: ConnectivityManagerNetworkMonitor,
    ): NetworkMonitor

    @Binds
    internal abstract fun bindsLocalComponentRepository(
        localComponentRepository: LocalComponentRepository,
    ): ComponentRepository

    @Binds
    internal abstract fun bindsLocalAppRepository(
        localAppRepository: LocalAppRepository,
    ): AppRepository

    @Binds
    internal abstract fun bindLocalGeneralRuleDataSource(
        localGeneralRuleDataSource: LocalGeneralRuleDataSource,
    ): GeneralRuleDataSource

    @Binds
    internal abstract fun bindPermissionMonitor(
        permissionMonitor: AppPermissionMonitor,
    ): PermissionMonitor

    @Binds
    internal abstract fun bindAppStateCache(
        appStateCache: AppStateCache,
    ): IAppStateCache

    @Binds
    internal abstract fun bindTimeZoneMonitor(impl: TimeZoneBroadcastMonitor): TimeZoneMonitor

    @Binds
    internal abstract fun bindLicensesFetcher(impl: AndroidLicensesFetcherImpl): LicensesFetcher

    @Binds
    internal abstract fun bindLicensesRepository(
        licensesRepository: LocalLicensesRepository,
    ): LicensesRepository
}
