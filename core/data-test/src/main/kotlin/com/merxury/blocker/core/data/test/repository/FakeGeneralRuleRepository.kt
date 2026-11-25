/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.data.test.repository

import com.merxury.blocker.core.data.repository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.model.data.GeneralRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

/**
 * A fixed set of rules for instrumented tests.
 */

class FakeGeneralRuleRepository @Inject constructor() : GeneralRuleRepository {
    private val generalRules = listOf(
        GeneralRule(
            id = 1,
            name = "Pangolin Advertising SDK",
            iconUrl = "/files/catalog/icon-1/icon/chuanshanjia.svg",
            company = "Beijing Juliang Engine Network Technology Co., Ltd.",
            searchKeyword = listOf(
                "com.ss.android.socialbase.",
                "com.ss.android.downloadlib.",
                "com.bytedance.embedapplog.",
                "com.bytedance.pangle.",
                "com.bytedance.tea.crash.",
                "com.bytedance.sdk.openadsdk.",
            ),
            description = "Pangolin is a global developer growth platform, providing global developers with full life cycle services and growth solutions such as user growth, traffic monetization, and LTV improvement.",
            safeToBlock = true,
            sideEffect = "Unknown",
            contributors = listOf("tester"),
        ),
        GeneralRule(
            id = 2,
            name = "SuperSonic",
            iconUrl = "/files/catalog/icon-1/icon/supersonic.png",
            company = "ironSource",
            searchKeyword = listOf(".supersonicads."),
            description = "",
            safeToBlock = true,
            sideEffect = "Unknown",
            contributors = listOf("tester"),
        ),
        GeneralRule(
            id = 3,
            name = "DataFinder analysis",
            iconUrl = "/files/catalog/icon-1/icon/volcengine.png",
            company = "volcano engine",
            searchKeyword = listOf("com.bytedance.applog."),
            description = "Volcano Engine Growth Analysis \"Event Analysis\" supports multi-dimensional analysis of user behavior through custom indicators, grouping and filtering, and various visual charts. At the same time, it provides abnormal data analysis functions in terms of data intelligence insights, which can help us discover more data The impact of the exception.",
            safeToBlock = true,
            sideEffect = "Unknown",
            contributors = listOf("tester"),
        ),
        GeneralRule(
            id = 4,
            name = "Sensory Analysis",
            iconUrl = "/files/catalog/icon-1/icon/sensorsdata.png",
            company = "Sensors Data",
            searchKeyword = listOf("com.sensorsdata.analytics."),
            description = "Based on your business characteristics and multi-department composite needs, Sensors Analytics helps you establish an efficient data indicator system, abstracts user behavior with advanced event models, provides multi-dimensional, multi-indicator cross-analysis capabilities, and fully supports the daily data of each team Analyze requirements to drive business decisions.",
            safeToBlock = true,
            sideEffect = "Unknown",
            contributors = listOf("tester"),
        ),
        GeneralRule(
            id = 5,
            name = "Ali mobile data analysis",
            iconUrl = "/files/catalog/icon-1/icon/alianalytics.png",
            company = "Alibaba Cloud",
            searchKeyword = listOf("com.alibaba.analytics."),
            description = "Mobile Analytics Android SDK is a data statistics and monitoring service under the Android platform provided by Alibaba Cloud for mobile developers. Through the SDK, developers can conveniently bury data in their own APP, monitor daily business data and network performance data, and observe the corresponding data report display through the Alibaba Cloud console interface.",
            safeToBlock = true,
            sideEffect = "Unknown",
            contributors = listOf("tester"),
        ),
        GeneralRule(
            id = 6,
            name = "Ali mobile push",
            iconUrl = "/files/catalog/icon-1/icon/alianalytics.png",
            company = "Alibaba Cloud",
            searchKeyword = listOf(
                "com.alibaba.sdk.android.push.",
                "com.taobao.accs.",
                "com.taobao.agoo.",
                "org.android.agoo.accs.",
            ),
            description = "Mobile push (Mobile Push) is a mobile message push service provided to mobile developers. By integrating the push function in the App, it can perform efficient, accurate and real-time message push, so that the business can reach users in a timely manner and improve user stickiness.",
            safeToBlock = true,
            sideEffect = "Unknown",
            contributors = listOf("tester"),
        ),
    )

    override fun getGeneralRules(): Flow<List<GeneralRule>> = flowOf(generalRules)

    override fun getGeneralRule(id: Int): Flow<GeneralRule> = getGeneralRules().mapNotNull { it.find { rule -> rule.id == id } }

    override fun searchGeneralRule(keyword: String): Flow<List<GeneralRule>> = getGeneralRules().map { rules ->
        rules.filter { it.name.contains(keyword, ignoreCase = true) }
    }
}
