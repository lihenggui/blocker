package com.merxury.blocker.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.merxury.blocker.data.source.GeneralRuleRepository
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
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun provideOnlineRuleRetrofit(@ApplicationContext context: Context, gson: Gson): Retrofit {
        val sourceType = PreferenceUtil.getOnlineSourceType(context)
        return Retrofit.Builder()
            .baseUrl(sourceType.baseUrl)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    fun provideGeneralRuleService(retrofit: Retrofit): GeneralRuleService {
        return retrofit.create(GeneralRuleService::class.java)
    }

    @Provides
    fun providesGson(): Gson {
        return GsonBuilder().create()
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

}