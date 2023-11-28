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

package com.merxury.blocker.core.data.di

import com.merxury.blocker.core.data.appstate.AppStateCache
import com.merxury.blocker.core.data.appstate.IAppStateCache
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.app.LocalAppRepository
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.component.LocalComponentRepository
import com.merxury.blocker.core.data.respository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.data.respository.componentdetail.IComponentDetailRepository
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleDataSource
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.respository.generalrule.LocalGeneralRuleDataSource
import com.merxury.blocker.core.data.respository.generalrule.OfflineFirstGeneralRuleRepository
import com.merxury.blocker.core.data.respository.userdata.AppPropertiesRepository
import com.merxury.blocker.core.data.respository.userdata.LocalAppPropertiesRepository
import com.merxury.blocker.core.data.respository.userdata.LocalUserDataRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.data.util.ConnectivityManagerNetworkMonitor
import com.merxury.blocker.core.data.util.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds
    fun bindUserDataRepository(
        userDataRepository: LocalUserDataRepository,
    ): UserDataRepository

    @Binds
    fun bindAppPropertiesRepository(
        appPropertiesRepository: LocalAppPropertiesRepository,
    ): AppPropertiesRepository

    @Binds
    fun bindsGeneralRuleRepository(
        generalRuleRepository: OfflineFirstGeneralRuleRepository,
    ): GeneralRuleRepository

    @Binds
    fun bindsComponentDetailRepository(
        componentDetailRepository: ComponentDetailRepository,
    ): IComponentDetailRepository

    @Binds
    fun bindsNetworkMonitor(
        networkMonitor: ConnectivityManagerNetworkMonitor,
    ): NetworkMonitor

    @Binds
    fun bindsLocalComponentRepository(
        localComponentRepository: LocalComponentRepository,
    ): ComponentRepository

    @Binds
    fun bindsLocalAppRepository(
        localAppRepository: LocalAppRepository,
    ): AppRepository

    @Binds
    fun bindLocalGeneralRuleDataSource(
        localGeneralRuleDataSource: LocalGeneralRuleDataSource,
    ): GeneralRuleDataSource

    @Binds
    fun bindAppStateCache(
        appCacheState: AppStateCache,
    ): IAppStateCache
}
