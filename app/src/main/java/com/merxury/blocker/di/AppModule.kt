package com.merxury.blocker.di

import android.content.Context
import android.content.pm.PackageManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.merxury.blocker.data.app.AppComponentDao
import com.merxury.blocker.data.app.InstalledAppDao
import com.merxury.blocker.data.app.InstalledAppDatabase
import com.merxury.blocker.data.component.OnlineComponentDataRepository
import com.merxury.blocker.data.component.OnlineComponentDataService
import com.merxury.blocker.data.instantinfo.InstantComponentInfoDao
import com.merxury.blocker.data.instantinfo.InstantComponentInfoDatabase
import com.merxury.blocker.data.source.GeneralRuleRepository
import com.merxury.blocker.data.source.OnlineSourceType
import com.merxury.blocker.data.source.local.GeneralRuleDao
import com.merxury.blocker.data.source.local.GeneralRuleDatabase
import com.merxury.blocker.data.source.remote.GeneralRuleService
import com.merxury.blocker.data.source.remote.RuleRemoteDataSource
import com.merxury.blocker.util.PreferenceUtil
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
    fun provideOnlineRuleSourceType(@ApplicationContext context: Context): OnlineSourceType {
        return PreferenceUtil.getOnlineSourceType(context)
    }

    @Provides
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
        return builder.build()
    }

    @Provides
    fun provideOnlineRuleRetrofit(
        okHttpClient: OkHttpClient, gson: Gson, type: OnlineSourceType
    ): Retrofit {
        return Retrofit.Builder().client(okHttpClient).baseUrl(type.baseUrl)
            .addConverterFactory(GsonConverterFactory.create(gson)).build()
    }

    @Provides
    fun provideGeneralRuleService(retrofit: Retrofit): GeneralRuleService {
        return retrofit.create(GeneralRuleService::class.java)
    }

    @Provides
    fun provideOnlineComponentDataService(retrofit: Retrofit): OnlineComponentDataService {
        return retrofit.create(OnlineComponentDataService::class.java)
    }

    @Provides
    fun provideOnlineComponentDataRepository(
        service: OnlineComponentDataService
    ): OnlineComponentDataRepository {
        return OnlineComponentDataRepository(service)
    }

    @Provides
    fun providesGson(): Gson {
        return GsonBuilder().serializeNulls().create()
    }

    @Provides
    @Singleton
    fun provideInstalledAppDatabase(@ApplicationContext context: Context): InstalledAppDatabase {
        return InstalledAppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideInstalledAppDao(database: InstalledAppDatabase): InstalledAppDao {
        return database.installedAppDao()
    }

    @Provides
    fun provideAppComponentDao(database: InstalledAppDatabase): AppComponentDao {
        return database.appComponentDao()
    }

    @Provides
    @Singleton
    fun provideGeneralRuleDatabase(@ApplicationContext context: Context): GeneralRuleDatabase {
        return GeneralRuleDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideGeneralRuleDao(database: GeneralRuleDatabase): GeneralRuleDao {
        return database.generalRuleDao()
    }

    @Provides
    @Singleton
    fun provideRuleRemoteDataSource(service: GeneralRuleService): RuleRemoteDataSource {
        return RuleRemoteDataSource(service)
    }

    @Provides
    @Singleton
    fun provideGeneralRuleRepository(
        remoteDataSource: RuleRemoteDataSource, localDataSource: GeneralRuleDao
    ): GeneralRuleRepository {
        return GeneralRuleRepository(remoteDataSource, localDataSource)
    }

    @Provides
    @Singleton
    fun provideInstantComponentInfoDatabase(
        @ApplicationContext context: Context
    ): InstantComponentInfoDatabase {
        return InstantComponentInfoDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideInstantComponentInfoDao(
        database: InstantComponentInfoDatabase
    ): InstantComponentInfoDao {
        return database.instantComponentInfoDao()
    }

    @Provides
    @Singleton
    fun providePackageManager(@ApplicationContext context: Context): PackageManager {
        return context.packageManager
    }
}