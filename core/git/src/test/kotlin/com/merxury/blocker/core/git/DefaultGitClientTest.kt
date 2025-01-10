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
import org.eclipse.jgit.treewalk.TreeWalk
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultGitClientTest {

    private lateinit var repositoryInfo: RepositoryInfo
    private lateinit var gitAction: DefaultGitClient
    private lateinit var tempDir: File
    private lateinit var remoteDir: File
    private lateinit var remoteGit: Git

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
    fun givenRepositoryWithChanges_whenCommitChanges_thenChangesAreCommitted() = runTest {
        // Create a repository first
        gitAction.createGitRepository()

        // Add a file to the repository
        val file = File(tempDir, "${repositoryInfo.repoName}/test.txt")
        file.writeText("Hello, World!")
        val result = gitAction.commitChanges("Test commit")
        assertTrue(result)

        // Verify that the changes were committed
        val git = Git(FileRepository(File(tempDir, "${repositoryInfo.repoName}/.git")))
        val status = git.status().call()
        assertFalse(status.hasUncommittedChanges())

        // Verify commit message
        val commit = git.log().call().iterator().next()
        assertTrue(commit.fullMessage.contains("Test commit"))

        // Verify that the file was added in the commit
        val tree = commit.tree
        val treeWalk = TreeWalk(git.repository)
        treeWalk.addTree(tree)
        treeWalk.isRecursive = true
        var found = false
        while (treeWalk.next()) {
            if (treeWalk.pathString == "test.txt") {
                found = true
                break
            }
        }
        assertTrue(found)

        // Verify that no local changes are detected
        val resultNoChanges = gitAction.hasLocalChanges()
        assertFalse(resultNoChanges)
    }

    @Test
    fun givenRepositoryWithChanges_whenHasLocalChanges_thenTrue() = runTest {
        // Create a repository first
        gitAction.createGitRepository()

        // Add a file to the repository
        val file = File(tempDir, "${repositoryInfo.repoName}/test.txt")
        file.writeText("Hello, World!")
        val result = gitAction.commitChanges("Test commit")
        assertTrue(result)

        assertFalse(gitAction.hasLocalChanges())

        // Write new content to the file
        file.writeText("Hello, World! Again!")
        assertTrue(gitAction.hasLocalChanges())
    }

    @Test
    fun givenFileAddedToRepository_whenAdd_thenFileIsAdded() = runTest {
        gitAction.createGitRepository()

        // Add a file to the repository
        val file = File(tempDir, "${repositoryInfo.repoName}/test.txt")
        file.writeText("Hello, World!")
        val result = gitAction.add(".")
        assertTrue(result == 1)

        // Verify that the file was added
        val git = Git(FileRepository(File(tempDir, "${repositoryInfo.repoName}/.git")))
        val status = git.status().call()
        assertTrue(status.added.contains("test.txt"))
    }

    @Test
    fun givenBranchExists_whenCheckout_thenBranchIsCheckedOut() = runTest {
        val mainBranchName = "main"
        gitAction.createGitRepository()
        // An empty repository does not have a HEAD yet
        // We should commit something first
        val file = File(tempDir, "${repositoryInfo.repoName}/test.txt")
        file.writeText("Hello, World!")
        gitAction.add(".")
        gitAction.commitChanges("Initial commit")
        gitAction.renameBranch(mainBranchName)

        val testBranchName = "test-branch"
        val createResult = gitAction.createBranch(testBranchName)
        assertTrue(createResult)

        val checkoutResult = gitAction.checkoutLocalBranch(testBranchName)
        assertTrue(checkoutResult)
        assertEquals(testBranchName, gitAction.getCurrentBranch())

        val resultMain = gitAction.checkoutLocalBranch("main")
        assertTrue(resultMain)
        assertEquals(mainBranchName, gitAction.getCurrentBranch())
    }

    @Test
    fun givenBranchNotExists_whenGetCurrentBranch_thenReturnMaster() = runTest {
        gitAction.createGitRepository()
        // For local repository, the default branch is master
        assertEquals("master", gitAction.getCurrentBranch())
    }

    @Test
    fun givenBranchExists_whenRenameBranch_thenBranchIsRenamed() = runTest {
        gitAction.createGitRepository()
        // An empty repository does not have a HEAD yet
        // We should commit something first
        val file = File(tempDir, "${repositoryInfo.repoName}/test.txt")
        file.writeText("Hello, World!")
        gitAction.add(".")
        gitAction.commitChanges("Initial commit")
        val mainBranchName = "main"
        gitAction.renameBranch(mainBranchName)
        assertEquals(mainBranchName, gitAction.getCurrentBranch())
    }

    @Test
    fun givenRemoteDoesNotExist_whenSetRemote_thenRemoteIsSet() = runTest {
        gitAction.createGitRepository()
        val url = "https://www.example.com/repo.git"
        val name = "example"
        val result = gitAction.setRemote(url, name)
        assertTrue(result)

        // Verify that the remote was set
        val git = Git(FileRepository(File(tempDir, "${repositoryInfo.repoName}/.git")))
        val remoteConfig = git.remoteList().call()
        assertTrue(remoteConfig.any { it.name == name && it.urIs.contains(URIish(url)) })
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
    fun givenValidRemoteRepository_whenPull_thenRepositoryIsPulled() = runTest {
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

        // Pull the changes
        val result = gitAction.pull()
        assertTrue(result)

        // Check the file is pulled in the local folder
        val targetFile = File(tempDir, "${repositoryInfo.repoName}/$newFileName")
        assertTrue(targetFile.exists())

        // Check file content
        assertEquals(fileContent, targetFile.readText())
    }

    // Test for fetchAndMergeFromMain
    @Test
    fun givenValidRemoteRepository_whenFetchAndMergeFromMain_thenRepositoryIsMerged() = runTest {
        gitAction.cloneRepository()

        // Create a new branch and make changes
        val newBranchName = "new-branch"
        gitAction.createBranch(newBranchName)
        val newFileName = "new.txt"
        val fileContent = "Hello, World!"
        val file = File(tempDir, "${repositoryInfo.repoName}/$newFileName")
        file.writeText(fileContent)
        gitAction.add(".")
        gitAction.commitChanges("New commit")

        // Add a new commit on the remote repository
        val remoteChangedFileName = "remote-changed.txt"
        val remoteChangedFileContent = "Hello, World! Remote"
        val remoteChangedFile = File(remoteDir, remoteChangedFileName)
        remoteChangedFile.writeText(remoteChangedFileContent)
        remoteGit.add()
            .addFilepattern(".")
            .call()
        remoteGit.commit()
            .setMessage("Remote commit")
            .call()

        // Fetch and merge from main
        val result = gitAction.fetchAndMergeFromMain()
        assertEquals(MergeStatus.MERGED, result)

        // Check the file is pulled in the local folder
        val targetFile = File(tempDir, "${repositoryInfo.repoName}/$remoteChangedFileName")
        assertTrue(targetFile.exists())
    }
}
