package com.merxury.blocker.ui.strategy.service

import com.merxury.blocker.ui.strategy.entity.ComponentDescription
import com.merxury.blocker.ui.strategy.entity.Strategy
import com.merxury.blocker.ui.strategy.entity.view.ComponentBriefInfo
import com.merxury.blocker.ui.strategy.entity.view.Result
import io.reactivex.Observable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.QueryMap

interface IClientServer {
    @GET("/components")
    fun findAllComponentsByPackageName(@QueryMap packageName: String): Observable<Result>

    @GET("/component")
    fun findComponentComments(@QueryMap component: HashMap<String, String>): Observable<Result>

    @POST("/component/upvote")
    fun upVoteForComponent(@Body component: ComponentBriefInfo): Observable<Result>

    @POST("/component/downvote")
    fun downVoteForComponent(@Body component: ComponentBriefInfo): Observable<Result>

    @GET("/description")
    fun getAllDescriptionsForComponent(@QueryMap component: HashMap<String, String>): Observable<Result>

    @POST("/description")
    fun addDescription(@Body description: ComponentDescription): Observable<Result>

    @POST("/description/upvote")
    fun upVoteForDescription(@Body id: Long): Observable<Result>

    @POST("/description/downvote")
    fun downVoteForDescription(@Body id: Long): Observable<Result>

    @POST("/strategy")
    fun addStrategy(@Body strategy: Strategy): Observable<Result>

    @POST("/strategy/upvote")
    fun upVoteForStrategy(@Body id: Long): Observable<Result>

    @POST("/strategy/downvote")
    fun downVoteForStrategy(@Body id: Long): Observable<Result>

    @POST("/strategy/auto")
    fun autoFindStrategy(@Body packageNames: List<String>): List<Strategy>
}