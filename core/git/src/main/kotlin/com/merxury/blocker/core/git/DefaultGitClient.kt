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

package com.merxury.blocker.core.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.jetbrains.annotations.TestOnly
import timber.log.Timber
import java.io.File

/** Default implementation of Git using JGit */
class DefaultGitClient(private val repoInfo: RepositoryInfo, baseDirectory: File) : GitClient {
    private val gitRepository = File(baseDirectory, repoInfo.name)
    private val gitFolder = File(gitRepository, ".git")

    override suspend fun cloneRepository(): Boolean {
        Git.cloneRepository()
            .setURI(repoInfo.url)
            .setDirectory(gitRepository)
            .call()
        return true
    }

    override suspend fun commitChanges(commitMessage: String): Boolean {
        val git = Git(FileRepository(gitFolder))
        git.add()
            .addFilepattern(".")
            .call()
        git.commit()
            .setMessage(commitMessage)
            .call()
        return true
    }

    override suspend fun hasLocalChanges(): Boolean {
        val git = Git(FileRepository(gitFolder))
        val status = git.status()
            .call()
        return status.hasUncommittedChanges()
    }

    override suspend fun add(filePattern: String): Int {
        val git = Git(FileRepository(gitFolder))
        git.add()
            .addFilepattern(filePattern)
            .call()
        val status = git.status()
            .call()
        return status.added.size + status.changed.size + status.removed.size + status.missing.size
    }

    override suspend fun checkoutLocalBranch(branchName: String): Boolean {
        // Check if it's a git repository
        if (!gitFolder.exists()) {
            Timber.e("$gitFolder is not a git repository")
            return false
        }
        val branchWithNamespace = "refs/heads/$branchName"
        Timber.d("Checking out branch $branchWithNamespace")
        // Find the branch list and check if the branch exists
        val branches = Git(FileRepository(gitFolder)).branchList().call()
        if (branches.none { it.name == branchWithNamespace }) {
            Timber.e("Branch $branchWithNamespace does not exist")
            return false
        }
        val git = Git(FileRepository(gitFolder))
        git.checkout()
            .setName(branchWithNamespace)
            .call()
        return true
    }

    override suspend fun createBranch(branchName: String): Boolean {
        if (!gitFolder.exists()) {
            Timber.e("$gitFolder is not a git repository")
            return false
        }
        Timber.d("Creating branch $branchName")
        val git = Git(FileRepository(gitFolder))
        git.checkout()
            .setCreateBranch(true)
            .setName(branchName)
            .call()
        return true
    }

    override suspend fun renameBranch(name: String): Boolean {
        if (!gitFolder.exists()) {
            Timber.e("$gitFolder is not a git repository")
            return false
        }
        Timber.d("Renaming branch to $name")
        val git = Git(FileRepository(gitFolder))
        git.branchRename()
            .setNewName(name)
            .call()
        return true
    }

    override suspend fun getCurrentBranch(): String? {
        if (!gitFolder.exists()) {
            Timber.e("$gitFolder is not a git repository")
            return null
        }
        val git = Git(FileRepository(gitFolder))
        return git.repository.branch
    }

    /**
     * Create a new git repository in the base directory
     */
    @TestOnly
    fun createGitRepository(): Boolean {
        Git.init()
            .setDirectory(gitRepository)
            .call()
        return true
    }
}
