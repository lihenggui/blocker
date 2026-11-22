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

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand.ResetType
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.transport.URIish
import org.jetbrains.annotations.TestOnly
import timber.log.Timber
import java.io.File

/** Default implementation of Git using JGit */
class DefaultGitClient(private val repoInfo: RepositoryInfo, baseDirectory: File) : GitClient {
    private val gitRepository = File(baseDirectory, repoInfo.repoName)
    private val gitFolder = File(gitRepository, ".git")

    /**
     * Opens a [FileRepository] for [gitFolder], executes [block] with a [Git] wrapper,
     * and guarantees the repository is closed afterwards.
     * Returns `null` (without running [block]) when the `.git` directory does not exist.
     */
    private inline fun <T> withGit(block: (Git) -> T): T? {
        if (!gitFolder.exists()) {
            Timber.e("$gitFolder is not a git repository")
            return null
        }
        val repo = FileRepository(gitFolder)
        return try {
            block(Git(repo))
        } finally {
            repo.close()
        }
    }

    override suspend fun cloneRepository(): Boolean {
        Timber.d("Cloning repository from ${repoInfo.url} to ${gitRepository.absolutePath}")
        val git = Git.cloneRepository()
            .setURI(repoInfo.url)
            .setRemote(repoInfo.remoteName)
            .setDirectory(gitRepository)
            .setBranch(repoInfo.branch)
            .setDepth(1)
            .call()
        git.close()
        Timber.d("Repository cloned successfully")
        return true
    }

    override suspend fun getHeadCommitId(): String? = withGit { git ->
        git.repository.resolve("HEAD")?.name
    }

    override suspend fun setRemote(url: String, name: String): Boolean = withGit { git ->
        Timber.i("Setting remote $name to $url")
        val existingRemotes = git.remoteList().call()
        if (existingRemotes.any { it.name == name }) {
            git.remoteSetUrl()
                .setRemoteName(name)
                .setRemoteUri(URIish(url))
                .call()
        } else {
            git.remoteAdd()
                .setName(name)
                .setUri(URIish(url))
                .call()
        }
        true
    } ?: false

    override suspend fun resetToRemote(remoteName: String, branch: String): Boolean = withGit { git ->
        Timber.i("Resetting to $remoteName/$branch")
        git.fetch()
            .setRemote(remoteName)
            .setRefSpecs("+refs/heads/$branch:refs/remotes/$remoteName/$branch")
            .setDepth(1)
            .call()
        git.reset()
            .setMode(ResetType.HARD)
            .setRef("refs/remotes/$remoteName/$branch")
            .call()
        // Update branch tracking configuration
        val config = git.repository.config
        config.setString("branch", branch, "remote", remoteName)
        config.setString("branch", branch, "merge", "refs/heads/$branch")
        config.save()
        Timber.i("Successfully reset to $remoteName/$branch")
        true
    } ?: false

    /**
     * Create a new git repository in the base directory
     */
    @TestOnly
    fun createGitRepository(): Boolean {
        val git = Git.init()
            .setDirectory(gitRepository)
            .call()
        git.close()
        return true
    }
}
