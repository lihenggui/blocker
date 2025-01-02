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

package com.merxury.blocker.core.git

interface GitClient {
    suspend fun cloneRepository(): Boolean
    suspend fun commitChanges(commitMessage: String): Boolean
    suspend fun hasLocalChanges(): Boolean
    suspend fun add(filePattern: String): Int
    suspend fun checkoutLocalBranch(branchName: String): Boolean
    suspend fun createBranch(branchName: String): Boolean
    suspend fun renameBranch(name: String): Boolean
    suspend fun getCurrentBranch(): String?
    suspend fun pull(): Boolean
    suspend fun fetchAndMergeFromMain(): MergeStatus
    suspend fun setRemote(url: String, name: String): Boolean
}
