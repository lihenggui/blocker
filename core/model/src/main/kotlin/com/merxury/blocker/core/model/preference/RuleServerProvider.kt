/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.model.preference

/**
 * Model representation for the online data provider
 */
enum class RuleServerProvider(
    val baseUrl: String,
    val commitApiUrl: String,
    val downloadLink: String,
) {
    GITHUB(
        baseUrl = "https://raw.githubusercontent.com/lihenggui/blocker-general-rules/main/",
        commitApiUrl = "https://api.github.com/repos/lihenggui/blocker-general-rules/commits",
        downloadLink = "https://github.com/lihenggui/blocker-general-rules/archive/refs/heads/main.zip",
    ),
    GITLAB(
        baseUrl = "https://gitlab.com/mercuryli/blocker-general-rules/-/raw/main/",
        commitApiUrl = "https://gitlab.com/api/v4/projects/54080457/repository/commits",
        downloadLink = "https://gitlab.com/mercuryli/blocker-general-rules/-/archive/main/blocker-general-rules-main.zip",
    ),
}
