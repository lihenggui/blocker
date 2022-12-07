/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.data.source

enum class OnlineSourceType(val baseUrl: String) {
    GITHUB("https://raw.githubusercontent.com/lihenggui/blocker-general-rules/online/"),
    GITLAB("https://jihulab.com/mercuryli/blocker-general-rules/-/raw/online/")
}
