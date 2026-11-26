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

import com.merxury.blocker.core.data.device.AppRestrictionRepository
import com.merxury.blocker.core.data.device.BackgroundUsageRepository
import com.merxury.blocker.core.data.device.ComponentActions
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.DebloaterApk
import com.merxury.blocker.core.data.device.DebloaterMetadataRepository
import com.merxury.blocker.core.data.device.DeviceUserRepository
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.device.LocalComponentActions
import com.merxury.blocker.core.data.device.PmBlockRecordSeeder
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.di.DataModule
import com.merxury.blocker.core.data.repository.catalog.CatalogRepository
import com.merxury.blocker.core.data.repository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.data.repository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.repository.licenses.LicensesRepository
import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import com.merxury.blocker.core.data.test.repository.FakeComponentDetailRepository
import com.merxury.blocker.core.data.test.repository.FakeGeneralRuleRepository
import com.merxury.blocker.core.data.test.repository.FakeLicensesRepository
import com.merxury.blocker.core.data.test.repository.FakeUserDataRepository
import com.merxury.blocker.core.data.util.NetworkMonitor
import com.merxury.blocker.core.data.util.PermissionMonitor
import com.merxury.blocker.core.data.util.TimeZoneMonitor
import com.merxury.blocker.core.data.wake.AutomaticWakeBlockRepository
import com.merxury.blocker.core.data.wake.WakeBlockRepository
import com.merxury.blocker.core.data.wake.WakeReceiverResolver
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.manifest.AndroidManifest
import com.merxury.blocker.core.testing.repository.TestCatalogRepository
import com.merxury.blocker.core.testing.repository.device.FakeAppRestrictionRepository
import com.merxury.blocker.core.testing.repository.device.FakeBackgroundUsageRepository
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.device.FakeDeviceUserRepository
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import com.merxury.blocker.core.testing.repository.device.FakePmBlockRecordSeeder
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.repository.wake.FakeAutomaticWakeBlockRepository
import com.merxury.blocker.core.testing.repository.wake.FakeWakeBlockRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DataModule::class],
)
internal interface TestDataModule {
    @Binds
    fun bindComponentActions(impl: LocalComponentActions): ComponentActions

    @Binds
    fun bindUserDataRepository(
        userDataRepository: FakeUserDataRepository,
    ): UserDataRepository

    @Binds
    fun bindsTestGeneralRuleRepository(
        testGeneralRuleRepository: FakeGeneralRuleRepository,
    ): GeneralRuleRepository

    @Binds
    fun bindsComponentDetailRepository(
        componentDetailRepository: FakeComponentDetailRepository,
    ): ComponentDetailRepository

    @Binds
    fun bindsNetworkMonitor(
        networkMonitor: AlwaysOnlineNetworkMonitor,
    ): NetworkMonitor

    @Binds
    fun bindsPermissionMonitor(
        permissionMonitor: AlwaysGrantedPermissionMonitor,
    ): PermissionMonitor

    @Binds
    fun bindsTimeZoneMonitor(impl: DefaultZoneIdTimeZoneMonitor): TimeZoneMonitor

    @Binds
    fun bindsLicensesRepository(
        licensesRepository: FakeLicensesRepository,
    ): LicensesRepository

    @Binds
    fun bindsDeviceUserRepository(repository: FakeDeviceUserRepository): DeviceUserRepository

    @Binds
    fun bindsInstalledAppRepository(repository: FakeInstalledAppRepository): InstalledAppRepository

    @Binds
    fun bindsComponentStatusRepository(repository: FakeComponentStatusRepository): ComponentStatusRepository

    @Binds
    fun bindsSelectedUserRepository(repository: FakeSelectedUserRepository): SelectedUserRepository

    @Binds
    fun bindsAppRestrictionRepository(repository: FakeAppRestrictionRepository): AppRestrictionRepository

    @Binds
    fun bindsBackgroundUsageRepository(repository: FakeBackgroundUsageRepository): BackgroundUsageRepository

    @Binds
    fun bindsPmBlockRecordSeeder(seeder: FakePmBlockRecordSeeder): PmBlockRecordSeeder

    companion object {
        @Provides
        @Singleton
        fun catalogRepository(): CatalogRepository = TestCatalogRepository()

        @Provides
        @Singleton
        fun wakeBlockRepository(): WakeBlockRepository = FakeWakeBlockRepository()

        @Provides
        @Singleton
        fun automaticWakeBlockRepository(): AutomaticWakeBlockRepository = FakeAutomaticWakeBlockRepository()

        @Provides
        fun wakeReceiverResolver(): WakeReceiverResolver = WakeReceiverResolver { _, _ -> emptyList() }

        @Provides
        fun debloaterMetadataRepository(): DebloaterMetadataRepository = object : DebloaterMetadataRepository {
            override suspend fun packageOrder(user: UserId): List<String> = emptyList()
            override suspend fun apk(app: AppKey): DebloaterApk? = null
            override suspend fun read(apk: DebloaterApk): AndroidManifest? = null
        }
    }
}
