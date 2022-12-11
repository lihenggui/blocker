/*
 * Copyright 2022 Blocker
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

import android.content.Context
import androidx.room.Room
import com.merxury.blocker.core.database.app.InstalledAppDatabase
import com.merxury.blocker.core.database.instantinfo.InstantComponentInfoDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideInstalledAppDatabase(
        @ApplicationContext context: Context
    ): InstalledAppDatabase {
        return Room.databaseBuilder(
            context,
            InstalledAppDatabase::class.java,
            "installed_app"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideInstantComponentInfoDatabase(
        @ApplicationContext context: Context
    ): InstantComponentInfoDatabase {
        return InstantComponentInfoDatabase.getInstance(context)
    }
}
