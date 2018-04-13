package com.merxury.blocker.ui.strategy.service

import com.merxury.blocker.ui.strategy.entity.ComponentDescription
import com.merxury.blocker.ui.strategy.entity.view.ComponentBriefInfo
import com.merxury.blocker.ui.strategy.entity.view.Result
import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.POST

interface IDescriptionServer {
    @GET("/description")
    fun getAllDescriptionsForComponent(component: ComponentBriefInfo): Observable<Result>

    @POST("/description")
    fun addDescription(description: ComponentDescription): Observable<Result>

    @POST("/description/upvote")
    fun upVote(id: Long): Observable<Result>

    @POST("/description/downvote")
    fun downVote(id: Long): Observable<Result>
}