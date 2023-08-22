/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.core.testing.testing.data

import com.merxury.blocker.core.model.data.GeneralRule

val generalRuleTestData = GeneralRule(
    id = 1,
    name = "AWS SDK for Kotlin (Developer Preview)",
    iconUrl = null,
    company = "Amazon",
    description = "The AWS SDK for Kotlin simplifies the use of AWS services by " +
        "providing a set of libraries that are consistent and familiar for " +
        "Kotlin developers. All AWS SDKs support API lifecycle considerations " +
        "such as credential management, retries, data marshaling, and serialization.",
    sideEffect = "Unknown",
    safeToBlock = true,
    contributors = listOf("Online contributor"),
    searchKeyword = listOf("androidx.google.example1"),
)

val generalRuleListTestData = listOf(
    generalRuleTestData,
    GeneralRule(
        id = 2,
        name = "Android WorkerManager",
        iconUrl = null,
        company = "Google",
        description = "WorkManager is the recommended solution for persistent work. " +
            "Work is persistent when it remains scheduled through app restarts and " +
            "system reboots. Because most background processing is best accomplished " +
            "through persistent work, WorkManager is the primary recommended API for " +
            "background processing.",
        sideEffect = "Background works won't be able to execute",
        safeToBlock = false,
        contributors = listOf("Google"),
        searchKeyword = listOf(
            "androidx.google.example1",
            "androidx.google.example2",
            "androidx.google.example3",
            "androidx.google.example4",
        ),
    ),
    GeneralRule(
        id = 3,
        name = "Pangolin Advertising SDK",
        iconUrl = "icon/chuanshanjia.svg",
        company = "Beijing Juliang Engine Network Technology Co., Ltd.",
        description = "Pangolin is a global developer growth platform, providing global developers with full life cycle services and growth solutions such as user growth, traffic monetization, and LTV improvement.",
        sideEffect = "Unknown",
        safeToBlock = false,
        contributors = listOf("Tester"),
        searchKeyword = listOf(
            "com.ss.android.socialbase.",
            "com.ss.android.downloadlib.",
            "com.bytedance.embedapplog.",
            "com.bytedance.pangle.",
            "com.bytedance.tea.crash.",
            "com.bytedance.sdk.openadsdk.",
        ),
    ),
    GeneralRule(
        id = 4,
        name = "Ali mobile push",
        iconUrl = "icon/alianalytics.png",
        company = "Alibaba Cloud",
        description = "Mobile push (Mobile Push) is a mobile message push service provided to mobile developers. By integrating the push function in the App, it can perform efficient, accurate and real-time message push, so that the business can reach users in a timely manner and improve user stickiness.",
        sideEffect = "Unknown",
        safeToBlock = false,
        contributors = listOf("Tester"),
        searchKeyword = listOf(
            "com.alibaba.sdk.android.push.",
            "com.taobao.accs.",
            "com.taobao.agoo.",
            "org.android.agoo.accs.",
        ),
    ),
    GeneralRule(
        id = 5,
        name = "HUAWEI Push Kit",
        iconUrl = null,
        company = "Huawei",
        description = "HUAWEI Push Kit is a messaging service provided for you to establish efficient communication channels with your users. You can send messages to your users in a reliable, secure, and high-speed manner.",
        sideEffect = "Unknown",
        safeToBlock = false,
        contributors = listOf("Tester"),
        searchKeyword = listOf(
            "com.huawei.hms.analytics",
            "com.huawei.hms.push",
            "com.huawei.hms.location",
            "com.huawei.hms.ads",
            "com.huawei.hms.flutter",
            "com.huawei.hms.flutter.analytics",
            "com.huawei.hms.flutter.ads",
            "com.huawei.hms.flutter.ar",
            "com.huawei.hms.flutter.auth",
            "com.huawei.hms.flutter.awareness",
        ),
    ),
)
