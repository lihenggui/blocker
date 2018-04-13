package com.merxury.blocker.ui.strategy.service

import com.merxury.blocker.ui.strategy.entity.Strategy
import com.merxury.blocker.ui.strategy.entity.view.Result
import io.reactivex.Observable
import retrofit2.http.POST

interface IStrategyServer {
    @POST("/strategy")
    fun addStrategy(strategy: Strategy): Observable<Result>

    @POST("/strategy/upvote")
    fun upVote(id: Long): Observable<Result>

    @POST("/strategy/downvote")
    fun downVote(id: Long): Observable<Result>
}