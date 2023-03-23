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

package com.merxury.blocker.core.database

import com.merxury.blocker.core.database.app.AppComponentDao
import com.merxury.blocker.core.database.app.InstalledAppDao
import com.merxury.blocker.core.database.app.InstalledAppDatabase
import com.merxury.blocker.core.database.cmpdetail.ComponentDetailDao
import com.merxury.blocker.core.database.cmpdetail.ComponentDetailDatabase
import com.merxury.blocker.core.database.generalrule.GeneralRuleDao
import com.merxury.blocker.core.database.generalrule.GeneralRuleDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DaosModule {
    @Provides
    fun provideInstalledAppDao(database: InstalledAppDatabase): InstalledAppDao {
        return database.installedAppDao()
    }

    @Provides
    fun provideAppComponentDao(database: InstalledAppDatabase): AppComponentDao {
        return database.appComponentDao()
    }

    @Provides
    @Singleton
    fun provideGeneralRuleDao(database: GeneralRuleDatabase): GeneralRuleDao {
        return database.generalRuleDao()
    }

    @Provides
    @Singleton
    fun provideComponentDetailDao(database: ComponentDetailDatabase): ComponentDetailDao {
        return database.componentDetailDao()
    }
}
