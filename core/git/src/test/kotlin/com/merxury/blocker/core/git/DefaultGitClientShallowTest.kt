/*
 * Copyright 2026 Blocker
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

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand.ResetType
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DefaultGitClientShallowTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var remote: Git
    private lateinit var repoInfo: RepositoryInfo
    private lateinit var checkout: File
    private lateinit var client: DefaultGitClient

    @Before
    fun setUp() {
        val remoteFolder = temporaryFolder.newFolder("remote")
        remote = Git.init().setDirectory(remoteFolder).setInitialBranch("main").call()
        repeat(3) { commit(remote, "Remote revision $it") }
        repoInfo = RepositoryInfo(
            remoteName = "origin",
            url = remoteFolder.toPath().toUri().toString(),
            repoName = "rules",
            branch = "main",
        )
        val baseDirectory = temporaryFolder.newFolder("local")
        checkout = baseDirectory.resolve(repoInfo.repoName)
        client = DefaultGitClient(repoInfo, baseDirectory)
    }

    @After
    fun tearDown() {
        remote.close()
    }

    @Test
    fun givenRemoteRepository_whenClone_thenCloneIsShallowAndHeadMatchesRemote() = runTest {
        assertThat(client.cloneRepository()).isTrue()

        val remoteHead = remote.repository.resolve("HEAD").name
        assertThat(client.getHeadCommitId()).isEqualTo(remoteHead)
        assertThat(checkout.resolve(".git/shallow").readLines()).containsExactly(remoteHead)
        Git.open(checkout).use { git ->
            assertThat(git.log().call().toList()).hasSize(1)
        }
    }

    @Test
    fun givenRemoteHeadOnOtherBranch_whenClone_thenUsesConfiguredBranch() = runTest {
        val mainHead = remote.repository.resolve("HEAD").name
        remote.checkout().setCreateBranch(true).setName("other").call()
        commit(remote, "Other branch")

        assertThat(client.cloneRepository()).isTrue()

        assertThat(client.getCurrentBranch()).isEqualTo("main")
        assertThat(client.getHeadCommitId()).isEqualTo(mainHead)
    }

    @Test
    fun givenMissingOrEmptyRepository_whenGetHeadCommitId_thenReturnsNull() = runTest {
        assertThat(client.getHeadCommitId()).isNull()
        Git.init().setDirectory(checkout).call().close()
        assertThat(client.getHeadCommitId()).isNull()
    }

    @Test
    fun givenShallowClone_whenResetToRemote_thenFetchesNewCommits() = runTest {
        assertThat(client.cloneRepository()).isTrue()
        commit(remote, "First update")
        val updatedHead = commit(remote, "Second update")

        assertThat(client.resetToRemote("origin", "main")).isTrue()

        assertThat(client.getHeadCommitId()).isEqualTo(updatedHead)
        assertThat(checkout.resolve("rules.txt").readText()).isEqualTo("Second update")
        Git.open(checkout).use { git ->
            assertThat(git.log().call().toList()).hasSize(1)
            assertThat(git.status().call().isClean).isTrue()
        }

        val nextHead = commit(remote, "Third update")
        assertThat(client.resetToRemote("origin", "main")).isTrue()
        assertThat(client.getHeadCommitId()).isEqualTo(nextHead)
    }

    @Test
    fun givenExistingFullClone_whenResetToRemote_thenFetchesNewCommits() = runTest {
        Git.cloneRepository()
            .setURI(repoInfo.url)
            .setDirectory(checkout)
            .call()
            .use { git ->
                assertThat(git.log().call().toList()).hasSize(3)
                assertThat(checkout.resolve(".git/shallow").exists()).isFalse()
            }
        commit(remote, "First update")
        val updatedHead = commit(remote, "Second update")

        assertThat(client.resetToRemote("origin", "main")).isTrue()

        assertThat(client.getHeadCommitId()).isEqualTo(updatedHead)
        assertThat(checkout.resolve("rules.txt").readText()).isEqualTo("Second update")
        Git.open(checkout).use { git ->
            assertThat(git.status().call().isClean).isTrue()
        }
    }

    @Test
    fun givenForcePushedRemoteAndDivergentLocalCommit_whenResetToRemote_thenMatchesRemote() = runTest {
        assertThat(client.cloneRepository()).isTrue()
        Git.open(checkout).use { git ->
            checkout.resolve("local-only.txt").writeText("Local commit")
            commit(git, "Divergent local revision")
        }
        val firstCommit = remote.log().call().last()
        remote.reset().setMode(ResetType.HARD).setRef(firstCommit.name).call()
        val rewrittenHead = commit(remote, "Rewritten remote history")

        assertThat(client.resetToRemote("origin", "main")).isTrue()

        assertThat(client.getHeadCommitId()).isEqualTo(rewrittenHead)
        assertThat(checkout.resolve("rules.txt").readText()).isEqualTo("Rewritten remote history")
        assertThat(checkout.resolve("local-only.txt").exists()).isFalse()
        Git.open(checkout).use { git ->
            assertThat(git.log().call().toList()).hasSize(1)
            assertThat(git.status().call().isClean).isTrue()
        }
    }

    @Test
    fun givenLocalChangesAndNoRemoteUpdate_whenResetToRemote_thenDiscardsLocalChanges() = runTest {
        assertThat(client.cloneRepository()).isTrue()
        checkout.resolve("rules.txt").writeText("Local modification")
        checkout.resolve("local-only.txt").writeText("Staged local file")
        Git.open(checkout).use { git ->
            git.add().addFilepattern("local-only.txt").call()
            assertThat(git.status().call().isClean).isFalse()
        }

        assertThat(client.resetToRemote("origin", "main")).isTrue()

        assertThat(client.getHeadCommitId()).isEqualTo(remote.repository.resolve("HEAD").name)
        assertThat(checkout.resolve("rules.txt").readText()).isEqualTo("Remote revision 2")
        assertThat(checkout.resolve("local-only.txt").exists()).isFalse()
        Git.open(checkout).use { git ->
            assertThat(git.status().call().isClean).isTrue()
        }
    }

    @Test
    fun givenProviderWithUnrelatedHistory_whenResetToRemote_thenSwitchesToProvider() = runTest {
        assertThat(client.cloneRepository()).isTrue()
        val otherFolder = temporaryFolder.newFolder("other-remote")
        Git.init().setDirectory(otherFolder).setInitialBranch("main").call().use { other ->
            otherFolder.resolve("other-only.txt").writeText("Other provider")
            val otherHead = commit(other, "Other provider rules")
            assertThat(client.setRemote(otherFolder.toPath().toUri().toString(), "GITLAB")).isTrue()

            assertThat(client.resetToRemote("GITLAB", "main")).isTrue()

            assertThat(client.getHeadCommitId()).isEqualTo(otherHead)
            assertThat(client.getTrackingRemote()).isEqualTo("GITLAB")
            assertThat(checkout.resolve("rules.txt").readText()).isEqualTo("Other provider rules")
            assertThat(checkout.resolve("other-only.txt").readText()).isEqualTo("Other provider")
        }
    }

    private fun commit(git: Git, content: String): String {
        git.repository.workTree.resolve("rules.txt").writeText(content)
        git.add().addFilepattern(".").call()
        return git.commit()
            .setAuthor("Blocker Test", "test@example.com")
            .setCommitter("Blocker Test", "test@example.com")
            .setMessage(content)
            .call()
            .name
    }
}
