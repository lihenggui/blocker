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

import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.transport.URIish
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultGitClientTest {

    private lateinit var repositoryInfo: RepositoryInfo
    private lateinit var gitAction: DefaultGitClient
    private lateinit var tempDir: File
    private lateinit var remoteDir: File
    private lateinit var remoteGit: Git

    /** A GitClient whose base directory has no .git folder — used for error path tests. */
    private lateinit var noGitDir: File
    private lateinit var noGitAction: DefaultGitClient

    @Before
    fun setUp() {
        tempDir = createTempDirectory().toFile()
        remoteDir = createTempDirectory().toFile()
        repositoryInfo = RepositoryInfo(
            remoteName = "origin",
            url = remoteDir.toURI().toString(),
            repoName = "repo",
            branch = "main",
        )
        gitAction = DefaultGitClient(repositoryInfo, tempDir)

        // Set up a base directory with no git repository for error-path tests
        noGitDir = createTempDirectory().toFile()
        noGitAction = DefaultGitClient(repositoryInfo, noGitDir)

        // Initialize remote repository
        remoteGit = Git.init()
            .setDirectory(remoteDir)
            .call()

        val file = File(remoteDir, "test.txt")
        file.writeText("Hello, World!")
        remoteGit.add()
            .addFilepattern(".")
            .call()
        remoteGit.commit()
            .setMessage("Initial commit")
            .call()
        // Rename the master branch to main
        remoteGit.branchRename()
            .setOldName("master")
            .setNewName("main")
            .call()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        remoteDir.deleteRecursively()
        noGitDir.deleteRecursively()
    }

    @Test
    fun givenNewRepository_whenCreateRepository_thenRepositoryIsCreated() = runTest {
        val result = gitAction.createGitRepository()
        assertTrue(result)

        // Verify that the repository was created
        val gitDir = File(tempDir, repositoryInfo.repoName)
        assertTrue(gitDir.exists())
        assertTrue(File(gitDir, ".git").exists())
    }

    @Test
    fun givenRemoteDoesNotExist_whenSetRemote_thenRemoteIsSetWithRefSpecs() = runTest {
        gitAction.createGitRepository()
        val url = "https://www.example.com/repo.git"
        val name = "example"
        val result = gitAction.setRemote(url, name)
        assertTrue(result)

        // Verify that the remote was set
        val git = Git(FileRepository(File(tempDir, "${repositoryInfo.repoName}/.git")))
        val remoteConfig = git.remoteList().call()
        val remote = remoteConfig.find { it.name == name }
        assertNotNull(remote)
        assertTrue(remote.urIs.contains(URIish(url)))
        // Verify fetch refspecs are present (remoteAdd sets these, remoteSetUrl does not)
        assertTrue(remote.fetchRefSpecs.isNotEmpty())
    }

    @Test
    fun givenRemoteExists_whenSetRemote_thenRemoteUrlIsUpdated() = runTest {
        gitAction.cloneRepository()
        val newUrl = "https://www.example.com/new-repo.git"
        val result = gitAction.setRemote(newUrl, repositoryInfo.remoteName)
        assertTrue(result)

        val git = Git(FileRepository(File(tempDir, "${repositoryInfo.repoName}/.git")))
        val remote = git.remoteList().call().find { it.name == repositoryInfo.remoteName }
        assertNotNull(remote)
        assertTrue(remote.urIs.contains(URIish(newUrl)))
    }

    @Test
    fun givenValidRemoteRepository_whenCloneRepository_thenRepositoryIsCloned() = runTest {
        val result = gitAction.cloneRepository()
        assertTrue(result)

        // Verify that the repository was cloned
        val gitDir = File(tempDir, repositoryInfo.repoName)
        assertTrue(gitDir.exists())
        assertTrue(File(gitDir, ".git").exists())
        assertTrue(File(gitDir, "test.txt").exists())
    }

    @Test
    fun givenValidRemoteRepository_whenResetToRemote_thenRepositoryIsUpdated() = runTest {
        gitAction.cloneRepository()

        // Add a new commit on the remote repository
        val newFileName = "new.txt"
        val fileContent = "Hello, World!"
        val file = File(remoteDir, newFileName)
        file.writeText(fileContent)
        remoteGit.add()
            .addFilepattern(".")
            .call()
        remoteGit.commit()
            .setMessage("New commit")
            .call()

        // Fetch and reset the cache
        val result = gitAction.resetToRemote(repositoryInfo.remoteName, repositoryInfo.branch)
        assertTrue(result)

        // Check the file is fetched into the local folder
        val targetFile = File(tempDir, "${repositoryInfo.repoName}/$newFileName")
        assertTrue(targetFile.exists())

        // Check file content
        assertEquals(fileContent, targetFile.readText())
    }

    @Test
    fun givenClonedFromOneRemote_whenResetToRemote_thenFilesMatchNewRemote() = runTest {
        // Clone from the original remote
        gitAction.cloneRepository()

        // Create a second remote repository with different content
        val secondRemoteDir = createTempDirectory().toFile()
        val secondRemoteGit = Git.init().setDirectory(secondRemoteDir).call()
        val secondRemoteFile = File(secondRemoteDir, "second.txt")
        secondRemoteFile.writeText("Content from second remote")
        secondRemoteGit.add().addFilepattern(".").call()
        secondRemoteGit.commit().setMessage("Initial commit on second remote").call()
        secondRemoteGit.branchRename().setOldName("master").setNewName("main").call()

        // Add the second remote and reset to it
        val secondRemoteName = "second"
        gitAction.setRemote(secondRemoteDir.toURI().toString(), secondRemoteName)
        val result = gitAction.resetToRemote(secondRemoteName, "main")
        assertTrue(result)

        // Verify local files match the second remote
        val localDir = File(tempDir, repositoryInfo.repoName)
        assertTrue(File(localDir, "second.txt").exists())
        assertEquals("Content from second remote", File(localDir, "second.txt").readText())
        // Original file from first remote should be gone
        assertFalse(File(localDir, "test.txt").exists())

        // Verify branch tracking config
        val git = Git(FileRepository(File(localDir, ".git")))
        val config = git.repository.config
        assertEquals(secondRemoteName, config.getString("branch", "main", "remote"))

        // Clean up
        secondRemoteDir.deleteRecursively()
    }

    // ── resetToRemote() error paths ──────────────────────────────────────

    @Test
    fun givenNoGitFolder_whenResetToRemote_thenReturnsFalse() = runTest {
        val result = noGitAction.resetToRemote("origin", "main")
        assertFalse(result)
    }

    @Test
    fun givenClonedRepo_whenResetToRemote_thenTrackingConfigIsUpdated() = runTest {
        gitAction.cloneRepository()

        // Create a second remote repository
        val secondRemoteDir = createTempDirectory().toFile()
        val secondRemoteGit = Git.init().setDirectory(secondRemoteDir).call()
        val secondRemoteFile = File(secondRemoteDir, "second.txt")
        secondRemoteFile.writeText("Content from second remote")
        secondRemoteGit.add().addFilepattern(".").call()
        secondRemoteGit.commit().setMessage("Initial commit on second remote").call()
        secondRemoteGit.branchRename().setOldName("master").setNewName("main").call()

        val secondRemoteName = "second"
        gitAction.setRemote(secondRemoteDir.toURI().toString(), secondRemoteName)
        gitAction.resetToRemote(secondRemoteName, "main")

        // Verify tracking config points to the new remote
        val repo = Git(FileRepository(File(tempDir, "${repositoryInfo.repoName}/.git")))
        assertEquals(secondRemoteName, repo.repository.config.getString("branch", "main", "remote"))

        secondRemoteDir.deleteRecursively()
    }

    // ── setRemote() error path ───────────────────────────────────────────

    @Test
    fun givenNoGitFolder_whenSetRemote_thenReturnsFalse() = runTest {
        val result = noGitAction.setRemote("https://example.com/repo.git", "origin")
        assertFalse(result)
    }
}
