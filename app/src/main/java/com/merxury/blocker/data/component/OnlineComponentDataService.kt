package com.merxury.blocker.data.component

import retrofit2.http.GET
import retrofit2.http.Path

interface OnlineComponentDataService {
    @GET("components/zh-cn/{path}")
    suspend fun getOnlineComponentData(@Path("path") relativePath: String): OnlineComponentData?
}