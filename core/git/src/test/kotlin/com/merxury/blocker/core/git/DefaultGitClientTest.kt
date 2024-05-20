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

import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.treewalk.TreeWalk
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultGitClientTest {

    private lateinit var repositoryInfo: RepositoryInfo
    private lateinit var baseDirectory: File
    private lateinit var gitAction: DefaultGitClient
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        repositoryInfo = RepositoryInfo("https://github.com/example/repo.git", "repo", "main")
        tempDir = createTempDirectory().toFile()
        baseDirectory = File(tempDir, "baseDirectory")
        baseDirectory.mkdirs()
        gitAction = DefaultGitClient(repositoryInfo, baseDirectory)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testCreateRepository() = runTest {
        val result = gitAction.createGitRepository()
        assertTrue(result)

        // Verify that the repository was created
        val gitDir = File(baseDirectory, repositoryInfo.name)
        assertTrue(gitDir.exists())
        assertTrue(File(gitDir, ".git").exists())
    }

    @Test
    fun testCommitChanges() = runTest {
        // Create a repository first
        gitAction.createGitRepository()

        // Add a file to the repository
        val file = File(baseDirectory, "${repositoryInfo.name}/test.txt")
        file.writeText("Hello, World!")
        val result = gitAction.commitChanges("Test commit")
        assertTrue(result)

        // Verify that the changes were committed
        val git = Git(FileRepository(File(baseDirectory, "${repositoryInfo.name}/.git")))
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
    }

    @Test
    fun testHasLocalChanges() = runTest {
        // Create a repository first
        gitAction.createGitRepository()

        // Add a file to the repository
        val file = File(baseDirectory, "${repositoryInfo.name}/test.txt")
        file.writeText("Hello, World!")
        gitAction.add(".")
        val result = gitAction.hasLocalChanges()
        assertTrue(result)

        // Commit the changes
        gitAction.commitChanges("Test commit")
        val resultNoChanges = gitAction.hasLocalChanges()
        assertFalse(resultNoChanges)
    }
}
