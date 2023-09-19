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

package com.merxury.blocker.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.merxury.blocker.core.database.instantinfo.InstantComponentInfoDao
import com.merxury.blocker.core.database.instantinfo.InstantComponentInfoDatabase
import com.merxury.blocker.core.network.model.OnlineSourceType
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
        return builder.build()
    }

    @Provides
    fun provideOnlineRuleRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
        type: OnlineSourceType,
    ): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(type.baseUrl)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    fun providesGson(): Gson {
        return GsonBuilder().serializeNulls().create()
    }

    @Provides
    fun provideInstantComponentInfoDao(
        database: InstantComponentInfoDatabase,
    ): InstantComponentInfoDao {
        return database.instantComponentInfoDao()
    }

    @Provides
    @Singleton
    fun provideInstantComponentInfoDatabase(
        @ApplicationContext context: Context,
    ): InstantComponentInfoDatabase {
        return Room.databaseBuilder(
            context,
            InstantComponentInfoDatabase::class.java,
            "instant_component_info",
        )
            .fallbackToDestructiveMigration()
            .build()
    }
}
