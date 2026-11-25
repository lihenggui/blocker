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

package com.merxury.blocker.core.data.di

import com.merxury.blocker.core.data.device.BackgroundUsageRepository
import com.merxury.blocker.core.data.device.ComponentActions
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.DebloaterMetadataRepository
import com.merxury.blocker.core.data.device.DeviceUserRepository
import com.merxury.blocker.core.data.device.InMemorySelectedUserRepository
import com.merxury.blocker.core.data.device.InstalledAppDbPmBlockRecordSeeder
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.device.LocalBackgroundUsageRepository
import com.merxury.blocker.core.data.device.LocalComponentActions
import com.merxury.blocker.core.data.device.LocalComponentStatusRepository
import com.merxury.blocker.core.data.device.LocalDebloaterMetadataRepository
import com.merxury.blocker.core.data.device.LocalDeviceUserRepository
import com.merxury.blocker.core.data.device.LocalInstalledAppRepository
import com.merxury.blocker.core.data.device.PmBlockRecordSeeder
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.licenses.fetcher.AndroidLicensesFetcherImpl
import com.merxury.blocker.core.data.licenses.fetcher.LicensesFetcher
import com.merxury.blocker.core.data.repository.catalog.CatalogHttpFetcher
import com.merxury.blocker.core.data.repository.catalog.CatalogRepository
import com.merxury.blocker.core.data.repository.catalog.LocalCatalogRepository
import com.merxury.blocker.core.data.repository.catalog.OkHttpCatalogFetcher
import com.merxury.blocker.core.data.repository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.data.repository.componentdetail.LocalComponentDetailRepository
import com.merxury.blocker.core.data.repository.generalrule.CatalogGeneralRuleRepository
import com.merxury.blocker.core.data.repository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.repository.licenses.LicensesRepository
import com.merxury.blocker.core.data.repository.licenses.LocalLicensesRepository
import com.merxury.blocker.core.data.repository.userdata.LocalUserDataRepository
import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import com.merxury.blocker.core.data.util.AppPermissionMonitor
import com.merxury.blocker.core.data.util.ConnectivityManagerNetworkMonitor
import com.merxury.blocker.core.data.util.NetworkMonitor
import com.merxury.blocker.core.data.util.PermissionMonitor
import com.merxury.blocker.core.data.util.TimeZoneBroadcastMonitor
import com.merxury.blocker.core.data.util.TimeZoneMonitor
import com.merxury.blocker.core.data.wake.LocalWakeBlockRepository
import com.merxury.blocker.core.data.wake.PackageManagerWakeReceiverResolver
import com.merxury.blocker.core.data.wake.WakeBlockRepository
import com.merxury.blocker.core.data.wake.WakeReceiverResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    internal abstract fun bindCatalogRepository(repository: LocalCatalogRepository): CatalogRepository

    @Binds
    internal abstract fun bindBackgroundUsageRepository(repository: LocalBackgroundUsageRepository): BackgroundUsageRepository

    @Binds
    internal abstract fun bindWakeBlockRepository(repository: LocalWakeBlockRepository): WakeBlockRepository

    @Binds
    internal abstract fun bindWakeReceiverResolver(resolver: PackageManagerWakeReceiverResolver): WakeReceiverResolver

    @Binds
    internal abstract fun bindCatalogHttpFetcher(fetcher: OkHttpCatalogFetcher): CatalogHttpFetcher

    @Binds
    internal abstract fun bindComponentActions(impl: LocalComponentActions): ComponentActions

    @Binds
    internal abstract fun bindUserDataRepository(
        userDataRepository: LocalUserDataRepository,
    ): UserDataRepository

    @Binds
    internal abstract fun bindsGeneralRuleRepository(
        generalRuleRepository: CatalogGeneralRuleRepository,
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
    internal abstract fun bindPermissionMonitor(
        permissionMonitor: AppPermissionMonitor,
    ): PermissionMonitor

    @Binds
    internal abstract fun bindTimeZoneMonitor(impl: TimeZoneBroadcastMonitor): TimeZoneMonitor

    @Binds
    internal abstract fun bindLicensesFetcher(impl: AndroidLicensesFetcherImpl): LicensesFetcher

    @Binds
    internal abstract fun bindLicensesRepository(
        licensesRepository: LocalLicensesRepository,
    ): LicensesRepository

    @Binds
    internal abstract fun bindDebloaterMetadataRepository(repository: LocalDebloaterMetadataRepository): DebloaterMetadataRepository

    @Binds
    internal abstract fun bindDeviceUserRepository(repository: LocalDeviceUserRepository): DeviceUserRepository

    @Binds
    internal abstract fun bindInstalledAppRepository(repository: LocalInstalledAppRepository): InstalledAppRepository

    @Binds
    internal abstract fun bindComponentStatusRepository(repository: LocalComponentStatusRepository): ComponentStatusRepository

    @Binds
    internal abstract fun bindSelectedUserRepository(repository: InMemorySelectedUserRepository): SelectedUserRepository

    @Binds
    internal abstract fun bindPmBlockRecordSeeder(seeder: InstalledAppDbPmBlockRecordSeeder): PmBlockRecordSeeder
}
