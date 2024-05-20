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
