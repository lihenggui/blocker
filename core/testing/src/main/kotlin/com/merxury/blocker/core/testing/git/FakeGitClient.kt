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

package com.merxury.blocker.core.testing.git

import com.merxury.blocker.core.git.GitClient
import com.merxury.blocker.core.git.MergeStatus

class FakeGitClient : GitClient {
    var cloneResult: Boolean = true
    var commitResult: Boolean = true
    var hasLocalChangesResult: Boolean = false
    var addResult: Int = 0
    var checkoutResult: Boolean = true
    var createBranchResult: Boolean = true
    var renameBranchResult: Boolean = true
    var currentBranch: String? = "main"
    var pullResult: Boolean = true
    var fetchAndMergeResult: MergeStatus = MergeStatus.MERGED
    var setRemoteResult: Boolean = true
    var resetToRemoteResult: Boolean = true
    var trackingRemote: String? = "origin"

    override suspend fun cloneRepository(): Boolean = cloneResult
    override suspend fun commitChanges(commitMessage: String): Boolean = commitResult
    override suspend fun hasLocalChanges(): Boolean = hasLocalChangesResult
    override suspend fun add(filePattern: String): Int = addResult
    override suspend fun checkoutLocalBranch(branchName: String): Boolean = checkoutResult
    override suspend fun createBranch(branchName: String): Boolean = createBranchResult
    override suspend fun renameBranch(name: String): Boolean = renameBranchResult
    override suspend fun getCurrentBranch(): String? = currentBranch
    override suspend fun pull(): Boolean = pullResult
    override suspend fun fetchAndMergeFromMain(): MergeStatus = fetchAndMergeResult
    override suspend fun setRemote(url: String, name: String): Boolean = setRemoteResult
    override suspend fun resetToRemote(remoteName: String, branch: String): Boolean = resetToRemoteResult
    override suspend fun getTrackingRemote(): String? = trackingRemote
}
