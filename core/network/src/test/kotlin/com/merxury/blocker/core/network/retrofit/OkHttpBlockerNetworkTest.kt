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

package com.merxury.blocker.core.network.retrofit

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.network.NetworkException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test

class OkHttpBlockerNetworkTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val network = OkHttpBlockerNetwork(
        okhttpCallFactory = dagger.Lazy { error("Not used in these tests") },
        networkJson = json,
    )

    @Test
    fun givenGitHubJson_whenGetLatestCommitId_thenReturnsSha() {
        val githubJson = """[{"sha": "abc123", "node_id": "xyz"}]"""
        val result = network.getLatestCommitId(RuleServerProvider.GITHUB, githubJson)
        assertThat(result).isEqualTo("abc123")
    }

    @Test
    fun givenGitLabJson_whenGetLatestCommitId_thenReturnsId() {
        val gitlabJson = """[{"id": "def456", "short_id": "def4"}]"""
        val result = network.getLatestCommitId(RuleServerProvider.GITLAB, gitlabJson)
        assertThat(result).isEqualTo("def456")
    }

    @Test
    fun givenEmptyJson_whenGetLatestCommitId_thenThrows() {
        assertThrows(NetworkException::class.java) {
            network.getLatestCommitId(RuleServerProvider.GITHUB, "")
        }
    }

    @Test
    fun givenBlankJson_whenGetLatestCommitId_thenThrows() {
        assertThrows(NetworkException::class.java) {
            network.getLatestCommitId(RuleServerProvider.GITHUB, "   ")
        }
    }

    @Test
    fun givenEmptyArray_whenGetLatestCommitId_thenThrows() {
        assertThrows(NetworkException::class.java) {
            network.getLatestCommitId(RuleServerProvider.GITHUB, "[]")
        }
    }

    @Test
    fun givenMissingShaField_whenGetLatestCommitId_thenThrows() {
        val json = """[{"node_id": "xyz"}]"""
        assertThrows(NetworkException::class.java) {
            network.getLatestCommitId(RuleServerProvider.GITHUB, json)
        }
    }

    @Test
    fun givenMissingIdField_whenGetLatestCommitId_thenThrows() {
        val json = """[{"short_id": "def4"}]"""
        assertThrows(NetworkException::class.java) {
            network.getLatestCommitId(RuleServerProvider.GITLAB, json)
        }
    }

    @Test
    fun givenInvalidJson_whenGetLatestCommitId_thenThrows() {
        assertThrows(Exception::class.java) {
            network.getLatestCommitId(RuleServerProvider.GITHUB, "not json")
        }
    }

    @Test
    fun givenGitHubJsonWithExtraFields_whenGetLatestCommitId_thenReturnsSha() {
        val githubJson = """[{
            "sha": "abc123",
            "node_id": "xyz",
            "commit": {"message": "test"},
            "author": {"login": "user"}
        }]"""
        val result = network.getLatestCommitId(RuleServerProvider.GITHUB, githubJson)
        assertThat(result).isEqualTo("abc123")
    }

    @Test
    fun givenMultipleCommits_whenGetLatestCommitId_thenReturnsFirst() {
        val githubJson = """[
            {"sha": "first123"},
            {"sha": "second456"}
        ]"""
        val result = network.getLatestCommitId(RuleServerProvider.GITHUB, githubJson)
        assertThat(result).isEqualTo("first123")
    }

    @Test
    fun givenBlankSha_whenGetLatestCommitId_thenThrows() {
        val githubJson = """[{"sha": ""}]"""
        assertThrows(NetworkException::class.java) {
            network.getLatestCommitId(RuleServerProvider.GITHUB, githubJson)
        }
    }
}
