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

package com.merxury.blocker.core.testing.di

import com.merxury.blocker.core.data.di.DataModule
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.componentdetail.IComponentDetailRepository
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.respository.userdata.AppPropertiesRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.testing.repository.fake.FakeAppPropertiesRepository
import com.merxury.blocker.core.testing.repository.fake.FakeAppRepository
import com.merxury.blocker.core.testing.repository.fake.FakeComponentDetailRepository
import com.merxury.blocker.core.testing.repository.fake.FakeComponentRepository
import com.merxury.blocker.core.testing.repository.fake.FakeGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.fake.FakeUserDataRepository
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
    ): IComponentDetailRepository

    @Binds
    fun bindsComponentRepository(
        componentRepository: FakeComponentRepository,
    ): ComponentRepository

    @Binds
    fun bindsAppRepository(
        appRepository: FakeAppRepository,
    ): AppRepository
}
