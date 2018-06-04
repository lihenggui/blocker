package com.merxury.blocker.strategy.service

import com.merxury.blocker.strategy.entity.ComponentDescription
import com.merxury.blocker.strategy.entity.Strategy
import com.merxury.blocker.strategy.entity.view.AppComponentInfo
import com.merxury.blocker.strategy.entity.view.ComponentBriefInfo
import com.merxury.blocker.strategy.entity.view.Result
import io.reactivex.Observable
import retrofit2.http.*

interface IClientServer {
    @GET("/components")
    fun findAllComponentsByPackageName(@Query("packageName") packageName: String): Observable<Result<AppComponentInfo>>

    @GET("/component")
    fun findComponentComments(@QueryMap component: HashMap<String, String>): Observable<Result<List<ComponentDescription>>>

    @POST("/component/upvote")
    fun upVoteForComponent(@Body component: ComponentBriefInfo): Observable<Result<Any>>

    @POST("/component/downvote")
    fun downVoteForComponent(@Body component: ComponentBriefInfo): Observable<Result<Any>>

    @GET("/description")
    fun getAllDescriptionsForComponent(@QueryMap component: HashMap<String, String>): Observable<List<ComponentDescription>>

    @POST("/description")
    fun addDescription(@Body description: ComponentDescription): Observable<Result<Any>>

    @POST("/description/upvote")
    fun upVoteForDescription(@Body id: Long): Observable<Result<Any>>

    @POST("/description/downvote")
    fun downVoteForDescription(@Body id: Long): Observable<Result<Any>>

    @POST("/strategy")
    fun addStrategy(@Body strategy: Strategy): Observable<Result<Any>>

    @POST("/strategy/upvote")
    fun upVoteForStrategy(@Body id: Long): Observable<Result<Any>>

    @POST("/strategy/downvote")
    fun downVoteForStrategy(@Body id: Long): Observable<Result<Any>>

    @POST("/strategy/auto")
    fun autoFindStrategy(@Body packageNames: List<String>): Observable<Result<List<Strategy>>>
}