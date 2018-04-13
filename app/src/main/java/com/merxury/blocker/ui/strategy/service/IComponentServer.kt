package com.merxury.blocker.ui.strategy.service

import com.merxury.blocker.ui.strategy.entity.view.ComponentBriefInfo
import com.merxury.blocker.ui.strategy.entity.view.Result
import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.POST

interface IComponentServer {
    @GET("/components")
    fun findAllComponentsByPackageName(packageName: String): Observable<Result>

    @GET("/component")
    fun findComponentComments(component: ComponentBriefInfo): Observable<Result>

    @POST("/component/upvote")
    fun upVoteForComponent(component: ComponentBriefInfo): Observable<Result>

    @POST("/component/downvote")
    fun downVoteForComponent(component: ComponentBriefInfo): Observable<Result>

}