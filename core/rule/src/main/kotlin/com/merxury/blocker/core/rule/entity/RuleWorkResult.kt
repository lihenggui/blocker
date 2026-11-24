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
    const val LEGACY_PM_RECORDS_UNREADABLE = 7

    /**
     * Output of every rule work that finishes: how many items it changed, such as backup files
     * written or components changed; the same number as the work's own count key.
     */
    const val PARAM_CHANGED_COUNT = "param_changed_count"

    /** Output: how many items could not be done; see the run's summary for what an item is. */
    const val PARAM_FAILED_COUNT = "param_failed_count"

    /**
     * Output: how many items were left alone on purpose, such as apps not installed, not counting
     * the system apps the settings exclude.
     */
    const val PARAM_SKIPPED_COUNT = "param_skipped_count"

    /** Output: how many entries or rules already were as the backup says. */
    const val PARAM_UNCHANGED_COUNT = "param_unchanged_count"

    /** Output: how many apps' state could not be read from the system, part of the failed count. */
    const val PARAM_UNREADABLE_APP_COUNT = "param_unreadable_app_count"

    /** Output: whether the Intent Firewall rules could not be reached at all during the run. */
    const val PARAM_FIREWALL_UNAVAILABLE = "param_firewall_unavailable"
}
