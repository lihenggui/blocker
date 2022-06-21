package com.merxury.blocker.di

import android.content.Context
import android.content.pm.PackageManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.merxury.blocker.data.app.AppComponentDao
import com.merxury.blocker.data.app.InstalledAppDao
import com.merxury.blocker.data.app.InstalledAppDatabase
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
    fun provideOnlineRuleRetrofit(
        gson: Gson,
        type: OnlineSourceType
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(type.baseUrl)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    fun provideGeneralRuleService(retrofit: Retrofit): GeneralRuleService {
        return retrofit.create(GeneralRuleService::class.java)
    }

    @Provides
    fun providesGson(): Gson {
        return GsonBuilder()
            .serializeNulls()
            .create()
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
        remoteDataSource: RuleRemoteDataSource,
        localDataSource: GeneralRuleDao
    ): GeneralRuleRepository {
        return GeneralRuleRepository(remoteDataSource, localDataSource)
    }

    @Provides
    @Singleton
    fun providePackageManager(@ApplicationContext context: Context): PackageManager {
        return context.packageManager
    }

}