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

package com.merxury.blocker.core.rule.entity

object RuleWorkResult {
    const val PARAM_WORK_RESULT = "param_work_result"
    const val STARTED = 0
    const val FINISHED = 1
    const val FOLDER_NOT_DEFINED = 2
    const val MISSING_ROOT_PERMISSION = 3
    const val MISSING_STORAGE_PERMISSION = 4
    const val UNEXPECTED_EXCEPTION = 5
    const val CANCELLED = 6
}
